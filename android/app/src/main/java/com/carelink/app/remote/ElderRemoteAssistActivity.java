package com.carelink.app.remote;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.carelink.app.data.local.pref.PreferenceManager;

public class ElderRemoteAssistActivity extends AppCompatActivity {
    public static final String EXTRA_AUTO_OPEN_INVITE = "extra_auto_open_invite";
    public static final String EXTRA_INVITE_FAMILY_ID = "extra_invite_family_id";
    private static final String TAG = "ElderRemoteAssist";

    private static volatile boolean sIsActive = false;

    private static final int STATE_IDLE = 0;
    private static final int STATE_WAITING = 1;
    private static final int STATE_CONNECTED = 2;

    private static final long CAPTURE_INIT_CHECK_INTERVAL_MS = 500L;
    private static final long CAPTURE_INIT_TIMEOUT_MS = 15000L;
    private static final long WS_RECONNECT_DELAY_MS = 1500L;

    private PreferenceManager preferenceManager;
    private WebSocketManager wsManager;
    private ScreenCaptureService captureService;

    private boolean isServiceBound = false;
    private boolean pendingAcceptInvite = false;
    private boolean acceptSent = false;
    private int currentState = STATE_IDLE;

    private String myUserId = "";
    private String connectedFamilyUserId = "";
    private String lastInviteFamilyUserId = "";
    private long lastInvitePromptAtMs = 0L;
    private long sentFrameCount = 0L;
    private long lastFrameStatAtMs = 0L;
    private long captureInitStartedAtMs = 0L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView tvStatus;
    private TextView tvFamilyInfo;
    private Button btnStartShare;
    private Button btnStopShare;
    private Button btnAccept;
    private Button btnReject;

    private AlertDialog inviteDialog;

    private final Runnable wsReconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || wsManager == null) {
                return;
            }
            if (!wsManager.isConnected()) {
                wsManager.connect();
            }
        }
    };

    private final Runnable pendingAcceptChecker = new Runnable() {
        @Override
        public void run() {
            if (!pendingAcceptInvite || connectedFamilyUserId.isEmpty() || isFinishing() || isDestroyed()) {
                return;
            }

            if (!isRemoteAssistConnected()) {
                ensureRemoteAssistConnection();
                tvStatus.setText("正在连接协助服务...");
                mainHandler.postDelayed(this, CAPTURE_INIT_CHECK_INTERVAL_MS);
                return;
            }

            if (hasActiveCapture()) {
                completePendingAcceptIfReady();
                return;
            }

            String captureError = getCaptureInitError();
            if (!captureError.isEmpty()) {
                Log.w(TAG, "Capture init failed while waiting accept, error=" + captureError
                        + ", familyUserId=" + connectedFamilyUserId);
                pendingAcceptInvite = false;
                acceptSent = false;
                cancelPendingAcceptCheck();
                captureInitStartedAtMs = 0L;
                tvStatus.setText("屏幕共享初始化失败：" + mapCaptureError(captureError));
                updateState(STATE_IDLE);
                return;
            }

            if (captureInitStartedAtMs > 0L
                    && System.currentTimeMillis() - captureInitStartedAtMs > CAPTURE_INIT_TIMEOUT_MS) {
                pendingAcceptInvite = false;
                acceptSent = false;
                cancelPendingAcceptCheck();
                captureInitStartedAtMs = 0L;
                tvStatus.setText("屏幕共享初始化超时，请重新授权后再试");
                updateState(STATE_IDLE);
                return;
            }

            tvStatus.setText("已授权，正在初始化屏幕共享...");
            mainHandler.postDelayed(this, CAPTURE_INIT_CHECK_INTERVAL_MS);
        }
    };

    public static boolean isActive() {
        return sIsActive;
    }

    private final ActivityResultLauncher<Intent> mediaProjectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    captureInitStartedAtMs = System.currentTimeMillis();
                    resetCaptureRetryState();
                    ScreenCaptureService.cacheProjectionPermission(result.getResultCode(), result.getData());
                    Log.d(TAG, "Projection permission granted, familyUserId=" + connectedFamilyUserId
                            + ", pendingAccept=" + pendingAcceptInvite);
                    Intent data = new Intent(this, ScreenCaptureService.class);
                    data.setAction(ScreenCaptureService.ACTION_START);
                    data.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.getResultCode());
                    data.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.getData());
                    ContextCompat.startForegroundService(this, data);
                    bindCaptureService();
                    schedulePendingAcceptCheck();
                } else {
                    Log.w(TAG, "Projection permission denied or empty result data");
                    pendingAcceptInvite = false;
                    acceptSent = false;
                    captureInitStartedAtMs = 0L;
                    Toast.makeText(this, "屏幕共享权限被拒绝", Toast.LENGTH_SHORT).show();
                }
            });

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            ScreenCaptureService.LocalBinder localBinder = (ScreenCaptureService.LocalBinder) binder;
            captureService = localBinder.getService();
            isServiceBound = true;

            captureService.setFrameCallback((jpegData, timestamp) -> {
                if (wsManager != null && wsManager.isConnected()) {
                    wsManager.sendFrame(jpegData);
                    sentFrameCount++;
                    long now = System.currentTimeMillis();
                    if (now - lastFrameStatAtMs >= 1000L) {
                        lastFrameStatAtMs = now;
                        runOnUiThread(() -> {
                            if (currentState == STATE_CONNECTED) {
                                tvFamilyInfo.setText("已向家属 " + connectedFamilyUserId + " 发送画面帧：" + sentFrameCount);
                            }
                        });
                    }
                }
            });

            runOnUiThread(() -> {
                tryStartCaptureFromCache();
                if (pendingAcceptInvite && !connectedFamilyUserId.isEmpty()) {
                    if (hasActiveCapture()) {
                        completePendingAcceptIfReady();
                    } else {
                        captureInitStartedAtMs = ensureCaptureInitStartedAt();
                        tvStatus.setText("已授权，正在初始化屏幕共享...");
                        schedulePendingAcceptCheck();
                    }
                    return;
                }

                if (hasActiveCapture()) {
                    if (currentState == STATE_CONNECTED) {
                        tvStatus.setText("家属已连接，正在共享屏幕");
                    } else {
                        updateState(STATE_WAITING);
                        tvStatus.setText("屏幕共享已开启，等待家属连接");
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            captureService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        preferenceManager = new PreferenceManager(this);
        myUserId = resolveCurrentUserId();

        wsManager = new WebSocketManager();
        wsManager.addListener(wsListener);

        buildUI();
        connectWebSocket();
        handleInviteFromIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        sIsActive = true;
        bindCaptureService();
        ensureRemoteAssistConnection();
    }

    @Override
    protected void onStop() {
        super.onStop();
        sIsActive = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInviteFromIntent(intent);
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(16));
        root.setBackgroundColor(0xFFF5F7FB);

        TextView title = new TextView(this);
        title.setText("老人端远程协助");
        title.setTextSize(22);
        title.setTextColor(0xFF1F2937);
        root.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setText("正在连接协助服务...");
        tvStatus.setTextSize(16);
        tvStatus.setTextColor(0xFF374151);
        tvStatus.setPadding(0, dp(8), 0, 0);
        root.addView(tvStatus);

        tvFamilyInfo = new TextView(this);
        tvFamilyInfo.setText("当前暂无家属协助请求");
        tvFamilyInfo.setTextSize(14);
        tvFamilyInfo.setTextColor(0xFF4B5563);
        tvFamilyInfo.setPadding(0, dp(8), 0, dp(12));
        root.addView(tvFamilyInfo);

        btnStartShare = createButton("开始共享屏幕", 0xFF3B82F6, 0xFFFFFFFF);
        btnStartShare.setOnClickListener(v -> requestScreenCapturePermission());
        root.addView(btnStartShare);

        btnStopShare = createButton("停止共享屏幕", 0xFFF59E0B, 0xFFFFFFFF);
        btnStopShare.setOnClickListener(v -> stopScreenShare(false));
        root.addView(btnStopShare);

        btnAccept = createButton("同意家属协助", 0xFF10B981, 0xFFFFFFFF);
        btnAccept.setOnClickListener(v -> acceptInvite());
        root.addView(btnAccept);

        btnReject = createButton("拒绝本次协助", 0xFFEF4444, 0xFFFFFFFF);
        btnReject.setOnClickListener(v -> rejectInvite());
        root.addView(btnReject);

        setContentView(root);
        updateState(STATE_IDLE);
    }

    private Button createButton(String text, int bgColor, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setBackgroundColor(bgColor);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(10);
        button.setLayoutParams(params);
        return button;
    }

    private void connectWebSocket() {
        wsManager.connect();
    }

    private void ensureRemoteAssistConnection() {
        mainHandler.removeCallbacks(wsReconnectRunnable);
        if (wsManager == null || wsManager.isConnected()) {
            return;
        }
        wsManager.connect();
        mainHandler.postDelayed(wsReconnectRunnable, WS_RECONNECT_DELAY_MS);
    }

    private boolean isRemoteAssistConnected() {
        return wsManager != null && wsManager.isConnected();
    }

    private void requestScreenCapturePermission() {
        resetCaptureRetryState();
        if (hasActiveCapture()) {
            schedulePendingAcceptCheck();
            return;
        }
        if (isServiceBound && captureService != null && captureService.isInitializing()) {
            captureInitStartedAtMs = ensureCaptureInitStartedAt();
            schedulePendingAcceptCheck();
            return;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            pendingAcceptInvite = false;
            acceptSent = false;
            cancelPendingAcceptCheck();
            captureInitStartedAtMs = 0L;
            Toast.makeText(this, "当前设备不支持屏幕共享", Toast.LENGTH_SHORT).show();
            return;
        }
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent());
    }

    private void bindCaptureService() {
        if (isServiceBound) {
            return;
        }
        Intent intent = new Intent(this, ScreenCaptureService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private boolean hasActiveCapture() {
        return captureService != null && captureService.isCapturing();
    }

    private void tryStartCaptureFromCache() {
        if (captureService == null || captureService.isCapturing() || captureService.isInitializing()) {
            return;
        }
        if (captureService.startCaptureFromCacheIfNeeded()) {
            captureInitStartedAtMs = 0L;
        }
    }

    private void schedulePendingAcceptCheck() {
        mainHandler.removeCallbacks(pendingAcceptChecker);
        if (!pendingAcceptInvite || connectedFamilyUserId.isEmpty()) {
            return;
        }
        mainHandler.post(pendingAcceptChecker);
    }

    private void cancelPendingAcceptCheck() {
        mainHandler.removeCallbacks(pendingAcceptChecker);
    }

    private void stopScreenShare(boolean silent) {
        resetCaptureRetryState();
        Intent intent = new Intent(this, ScreenCaptureService.class);
        intent.setAction(ScreenCaptureService.ACTION_STOP);
        startService(intent);
        captureInitStartedAtMs = 0L;
        pendingAcceptInvite = false;
        acceptSent = false;
        cancelPendingAcceptCheck();

        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        captureService = null;

        if (connectedFamilyUserId.trim().isEmpty()) {
            updateState(STATE_IDLE);
        } else {
            updateState(STATE_WAITING);
        }

        if (!silent) {
            tvStatus.setText("已停止屏幕共享");
        }
    }

    private void acceptInvite() {
        if (connectedFamilyUserId.isEmpty()) {
            Toast.makeText(this, "当前没有待处理的家属请求", Toast.LENGTH_SHORT).show();
            return;
        }

        resetCaptureRetryState();
        pendingAcceptInvite = true;
        acceptSent = false;
        Log.d(TAG, "Accept invite requested, familyUserId=" + connectedFamilyUserId
                + ", wsConnected=" + isRemoteAssistConnected()
                + ", hasCapture=" + hasActiveCapture());

        if (!hasActiveCapture()) {
            captureInitStartedAtMs = ensureCaptureInitStartedAt();
            tvStatus.setText("请先授权屏幕共享，再接通家属协助");
            requestScreenCapturePermission();
        }

        if (!isRemoteAssistConnected()) {
            tvStatus.setText("正在连接协助服务...");
            ensureRemoteAssistConnection();
        }

        schedulePendingAcceptCheck();
        completePendingAcceptIfReady();
    }

    private void completePendingAcceptIfReady() {
        if (!pendingAcceptInvite || acceptSent || connectedFamilyUserId.isEmpty()) {
            return;
        }
        if (!hasActiveCapture() || !isRemoteAssistConnected()) {
            return;
        }

        acceptSent = true;
        cancelPendingAcceptCheck();
        captureInitStartedAtMs = 0L;
        sentFrameCount = 0L;
        lastFrameStatAtMs = 0L;
        Log.d(TAG, "Completing pending accept, familyUserId=" + connectedFamilyUserId
                + ", myUserId=" + myUserId);

        wsManager.sendRegister("elder", myUserId);
        wsManager.sendInvite("elder", myUserId, connectedFamilyUserId);

        pendingAcceptInvite = false;
        updateState(STATE_CONNECTED);
        tvStatus.setText("已同意家属协助，正在共享屏幕");
    }

    private void rejectInvite() {
        pendingAcceptInvite = false;
        acceptSent = false;
        captureInitStartedAtMs = 0L;
        cancelPendingAcceptCheck();
        if (wsManager != null && wsManager.isConnected()) {
            wsManager.sendHangup();
        }
        connectedFamilyUserId = "";
        tvFamilyInfo.setText("已拒绝本次协助请求");
        updateState(hasActiveCapture() ? STATE_WAITING : STATE_IDLE);
    }

    private void showIncomingInviteDialog() {
        if (connectedFamilyUserId.isEmpty() || isFinishing() || isDestroyed()) {
            return;
        }

        if (inviteDialog != null && inviteDialog.isShowing()) {
            inviteDialog.dismiss();
        }

        String message = "家属 “" + connectedFamilyUserId + "” 请求远程协助，是否同意共享屏幕？";
        inviteDialog = new AlertDialog.Builder(this)
                .setTitle("收到协助请求")
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton("拒绝", (dialog, which) -> rejectInvite())
                .setPositiveButton("同意", (dialog, which) -> acceptInvite())
                .create();
        inviteDialog.show();
    }

    private void processIncomingInvite(String data) {
        String familyUserId = parseUserId(data, "family:");
        if (familyUserId.isEmpty()) {
            familyUserId = data == null ? "" : data.trim();
        }
        if (familyUserId.isEmpty()) {
            return;
        }

        connectedFamilyUserId = familyUserId;
        pendingAcceptInvite = false;
        acceptSent = false;
        tvFamilyInfo.setText("家属 “" + connectedFamilyUserId + "” 请求远程协助");
        updateState(hasActiveCapture() ? STATE_WAITING : STATE_IDLE);

        long now = System.currentTimeMillis();
        boolean duplicatePrompt = connectedFamilyUserId.equals(lastInviteFamilyUserId)
                && (now - lastInvitePromptAtMs) < 1500;
        if (!duplicatePrompt) {
            lastInviteFamilyUserId = connectedFamilyUserId;
            lastInvitePromptAtMs = now;
            showIncomingInviteDialog();
        }
    }

    private void handleInviteFromIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        boolean autoOpenInvite = intent.getBooleanExtra(EXTRA_AUTO_OPEN_INVITE, false);
        String familyUserId = intent.getStringExtra(EXTRA_INVITE_FAMILY_ID);
        if (familyUserId != null) {
            familyUserId = familyUserId.trim();
        }
        if (autoOpenInvite && familyUserId != null && !familyUserId.isEmpty()) {
            processIncomingInvite("family:" + familyUserId);
        }
        intent.removeExtra(EXTRA_AUTO_OPEN_INVITE);
        intent.removeExtra(EXTRA_INVITE_FAMILY_ID);
    }

    private void updateState(int state) {
        currentState = state;
        switch (state) {
            case STATE_IDLE:
                btnStartShare.setEnabled(true);
                btnStopShare.setEnabled(false);
                btnAccept.setEnabled(false);
                btnReject.setEnabled(false);
                break;
            case STATE_WAITING:
                btnStartShare.setEnabled(false);
                btnStopShare.setEnabled(true);
                btnAccept.setEnabled(!connectedFamilyUserId.isEmpty());
                btnReject.setEnabled(!connectedFamilyUserId.isEmpty());
                break;
            case STATE_CONNECTED:
                btnStartShare.setEnabled(false);
                btnStopShare.setEnabled(true);
                btnAccept.setEnabled(false);
                btnReject.setEnabled(true);
                break;
            default:
                break;
        }
    }

    private final WebSocketManager.WsListener wsListener = new WebSocketManager.WsListener() {
        @Override
        public void onConnected() {
            runOnUiThread(() -> {
                wsManager.sendRegister("elder", myUserId);
                tvStatus.setText("服务连接成功，等待家属协助请求");
                completePendingAcceptIfReady();
            });
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                if (currentState == STATE_CONNECTED || pendingAcceptInvite || !connectedFamilyUserId.isEmpty()) {
                    tvStatus.setText("协助服务已断开，正在重连...");
                    ensureRemoteAssistConnection();
                } else {
                    tvStatus.setText("协助服务已断开");
                }
                if (currentState == STATE_CONNECTED) {
                    acceptSent = false;
                    pendingAcceptInvite = !connectedFamilyUserId.isEmpty();
                    updateState(hasActiveCapture() ? STATE_WAITING : STATE_IDLE);
                    schedulePendingAcceptCheck();
                }
            });
        }

        @Override
        public void onMessage(String type, String data) {
            runOnUiThread(() -> handleMessage(type, data));
        }

        @Override
        public void onFrame(byte[] jpegData) {
            // Elder side only sends frames.
        }

        @Override
        public void onTouchEvent(float x, float y, String action) {
            runOnUiThread(() -> {
                String touchInfo = "家属操作：" + action + " (" + (int) (x * 100) + "%, " + (int) (y * 100) + "%)";
                tvStatus.setText("已连接，正在共享屏幕\n最近操作：" + touchInfo);
                String ack = action + ":" + x + ":" + y + ":" + System.currentTimeMillis();
                wsManager.sendTouchAck(ack);
            });
        }

        @Override
        public void onError(String error) {
            runOnUiThread(() -> {
                tvStatus.setText("连接错误：" + error);
                ensureRemoteAssistConnection();
            });
        }
    };

    private void handleMessage(String type, String data) {
        switch (type) {
            case "ack":
                completePendingAcceptIfReady();
                break;
            case "family_starting":
            case "incoming_invite":
                processIncomingInvite(data);
                break;
            case "connected":
            case "family_connected":
                updateState(STATE_CONNECTED);
                if (!hasActiveCapture()) {
                    pendingAcceptInvite = true;
                    acceptSent = false;
                    captureInitStartedAtMs = ensureCaptureInitStartedAt();
                    tvStatus.setText("家属已连接，正在启动屏幕采集...");
                    requestScreenCapturePermission();
                    break;
                }
                captureInitStartedAtMs = 0L;
                tvStatus.setText("家属已连接，正在共享屏幕");
                break;
            case "hangup":
                pendingAcceptInvite = false;
                acceptSent = false;
                captureInitStartedAtMs = 0L;
                connectedFamilyUserId = "";
                tvFamilyInfo.setText("当前暂无家属协助请求");
                updateState(hasActiveCapture() ? STATE_WAITING : STATE_IDLE);
                tvStatus.setText("家属已断开连接");
                break;
            case "error":
                tvStatus.setText("连接错误：" + mapErrorCode(data));
                if ("session_not_registered".equalsIgnoreCase(data == null ? "" : data.trim())) {
                    acceptSent = false;
                    pendingAcceptInvite = !connectedFamilyUserId.isEmpty();
                    ensureRemoteAssistConnection();
                    schedulePendingAcceptCheck();
                }
                break;
            default:
                break;
        }
    }

    private String parseUserId(String data, String prefix) {
        if (data == null) {
            return "";
        }
        if (data.startsWith(prefix)) {
            return data.substring(prefix.length()).trim();
        }
        return "";
    }

    private String mapErrorCode(String code) {
        String value = code == null ? "" : code.trim();
        if (value.isEmpty()) {
            return "未知错误";
        }
        switch (value) {
            case "family_offline":
                return "家属端不在线";
            case "elder_offline":
                return "老人端不在线";
            case "peer_not_connected":
                return "对端尚未建立连接";
            case "signaling_parse_failed":
                return "信令解析失败，请重新发起远程协助";
            case "session_not_registered":
                return "会话尚未完成注册，正在重试";
            default:
                return value;
        }
    }

    private long ensureCaptureInitStartedAt() {
        if (captureInitStartedAtMs <= 0L) {
            captureInitStartedAtMs = System.currentTimeMillis();
        }
        return captureInitStartedAtMs;
    }

    private String getCaptureInitError() {
        if (captureService == null) {
            return "";
        }
        String error = captureService.getLastError();
        return error == null ? "" : error.trim();
    }

    private void resetCaptureRetryState() {
        if (captureService != null) {
            captureService.clearLastError();
        }
    }

    private String mapCaptureError(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "未知错误";
        }
        switch (code.trim()) {
            case "screen_capture_permission_missing":
                return "系统未返回屏幕共享授权数据";
            case "media_projection_manager_unavailable":
                return "当前设备不支持屏幕投影服务";
            case "media_projection_unavailable":
                return "屏幕投影初始化失败，请重新授权";
            case "virtual_display_create_failed":
                return "虚拟屏幕创建失败";
            case "screen_capture_init_failed":
                return "屏幕采集启动失败";
            default:
                return code.trim();
        }
    }

    private String resolveCurrentUserId() {
        String idStr = preferenceManager.getUserIdStr();
        if (idStr != null && !idStr.trim().isEmpty()) {
            return idStr.trim();
        }
        long id = preferenceManager.getUserId();
        if (id > 0) {
            return String.valueOf(id);
        }
        String email = preferenceManager.getEmail();
        if (email != null && !email.trim().isEmpty()) {
            return email.trim();
        }
        return "elder_" + System.currentTimeMillis();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inviteDialog != null && inviteDialog.isShowing()) {
            inviteDialog.dismiss();
            inviteDialog = null;
        }
        mainHandler.removeCallbacks(wsReconnectRunnable);
        if (wsManager != null) {
            wsManager.removeListener(wsListener);
            wsManager.disconnect();
        }
        cancelPendingAcceptCheck();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        captureService = null;
        Intent intent = new Intent(this, ScreenCaptureService.class);
        intent.setAction(ScreenCaptureService.ACTION_STOP);
        startService(intent);
    }
}
