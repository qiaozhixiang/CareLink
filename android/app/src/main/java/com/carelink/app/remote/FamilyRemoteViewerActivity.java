package com.carelink.app.remote;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.carelink.app.R;
import com.carelink.app.data.local.pref.PreferenceManager;

public class FamilyRemoteViewerActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private WebSocketManager wsManager;

    private String myUserId = "";
    private String targetElderId = "";
    private boolean isConnected = false;
    private boolean inviteAutoRetried = false;
    private boolean pendingConnectRequest = false;

    private ImageView ivScreen;
    private TextView tvStatus;
    private TextView tvConnectionInfo;
    private Button btnConnect;
    private Button btnDisconnect;
    private LinearLayout touchPad;

    private long lastFrameTime = 0L;
    private long lastFrameUiUpdateAt = 0L;
    private long firstFrameAt = 0L;
    private long receivedFrameCount = 0L;
    private static final long MIN_FRAME_INTERVAL = 100L;
    private static final long WS_RECONNECT_DELAY_MS = 1500L;
    private byte[] pendingFrame = null;
    private final Handler frameHandler = new Handler(Looper.getMainLooper());
    private final Runnable frameRenderer = () -> {
        if (pendingFrame != null) {
            displayFrame(pendingFrame);
            pendingFrame = null;
        }
    };
    private final Runnable firstFrameTimeoutChecker = new Runnable() {
        @Override
        public void run() {
            if (!isConnected) {
                return;
            }
            if (firstFrameAt <= 0L) {
                tvConnectionInfo.setText("连接已建立，但尚未收到画面帧，请确认老人端已授权共享并保持屏幕亮起");
                frameHandler.postDelayed(this, 3000);
            }
        }
    };
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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String intentElderId = getIntent().getStringExtra("elder_id");
        targetElderId = intentElderId == null ? "" : intentElderId.trim();

        preferenceManager = new PreferenceManager(this);
        myUserId = resolveCurrentUserId();

        wsManager = new WebSocketManager();
        wsManager.addListener(wsListener);

        buildUI();
        connectWebSocket();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF1A1A2E);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(16), dp(16), dp(16), dp(10));

        TextView title = new TextView(this);
        title.setText("远程协助（家属端）");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        top.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setText("正在连接远程协助服务...");
        tvStatus.setTextSize(15);
        tvStatus.setTextColor(0xFFDDDDDD);
        top.addView(tvStatus);

        tvConnectionInfo = new TextView(this);
        tvConnectionInfo.setText("");
        tvConnectionInfo.setTextSize(13);
        tvConnectionInfo.setTextColor(0xFF9ACBFF);
        top.addView(tvConnectionInfo);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        root.addView(top, topParams);

        ivScreen = new ImageView(this);
        ivScreen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivScreen.setImageDrawable(null);
        ivScreen.setBackgroundColor(0xFF111827);

        FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        imgParams.topMargin = dp(110);
        imgParams.bottomMargin = dp(120);
        root.addView(ivScreen, imgParams);

        touchPad = new LinearLayout(this);
        touchPad.setOrientation(LinearLayout.VERTICAL);
        touchPad.setBackgroundColor(0x00000000);
        touchPad.setOnTouchListener((v, event) -> handleTouch(event));
        root.addView(touchPad, imgParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(16), dp(10), dp(16), dp(16));

        btnConnect = createButton("连接老人屏幕", 0xFF10B981);
        btnConnect.setOnClickListener(v -> startConnect());
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        leftParams.rightMargin = dp(8);
        bottom.addView(btnConnect, leftParams);

        btnDisconnect = createButton("断开连接", 0xFFEF4444);
        btnDisconnect.setOnClickListener(v -> disconnectCall());
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        bottom.addView(btnDisconnect, rightParams);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        bottomParams.gravity = android.view.Gravity.BOTTOM;
        root.addView(bottom, bottomParams);

        setContentView(root);
        updateUiState();
    }

    private Button createButton(String text, int bgColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundColor(bgColor);
        return button;
    }

    private void connectWebSocket() {
        ensureRemoteAssistConnection();
    }

    private void ensureRemoteAssistConnection() {
        frameHandler.removeCallbacks(wsReconnectRunnable);
        if (wsManager == null || wsManager.isConnected()) {
            return;
        }
        wsManager.connect();
        frameHandler.postDelayed(wsReconnectRunnable, WS_RECONNECT_DELAY_MS);
    }

    private void startConnect() {
        if (!wsManager.isConnected()) {
            pendingConnectRequest = true;
            tvStatus.setText("正在连接远程协助服务...");
            ensureRemoteAssistConnection();
            return;
        }

        inviteAutoRetried = false;
        pendingConnectRequest = false;
        wsManager.sendInvite("family", myUserId, targetElderId);

        tvStatus.setText("已发起连接请求，等待老人端确认...");
        if (targetElderId.isEmpty()) {
            tvConnectionInfo.setText("目标老人：自动匹配在线老人");
        } else {
            tvConnectionInfo.setText("目标老人 ID：" + targetElderId);
        }
    }

    private void disconnectCall() {
        pendingConnectRequest = false;
        wsManager.sendHangup();
        isConnected = false;
        tvStatus.setText("已断开连接");
        tvConnectionInfo.setText("");
        frameHandler.removeCallbacks(firstFrameTimeoutChecker);
        updateUiState();
    }

    private void updateUiState() {
        btnConnect.setEnabled(!isConnected);
        btnDisconnect.setEnabled(isConnected);
        touchPad.setEnabled(isConnected);
    }

    private boolean handleTouch(MotionEvent event) {
        if (!isConnected || ivScreen.getWidth() <= 0 || ivScreen.getHeight() <= 0) {
            return false;
        }

        float x = clamp(event.getX() / ivScreen.getWidth());
        float y = clamp(event.getY() / ivScreen.getHeight());

        String action;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                action = "down";
                break;
            case MotionEvent.ACTION_MOVE:
                action = "move";
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                action = "up";
                break;
            default:
                return false;
        }

        wsManager.sendTouch(x, y, action);
        return true;
    }

    private float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private final WebSocketManager.WsListener wsListener = new WebSocketManager.WsListener() {
        @Override
        public void onConnected() {
            runOnUiThread(() -> {
                wsManager.sendRegister("family", myUserId);
                tvStatus.setText("服务连接成功，可发起远程协助");
                if (pendingConnectRequest) {
                    startConnect();
                }
            });
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                isConnected = false;
                tvStatus.setText("协助服务已断开，正在重连...");
                tvConnectionInfo.setText("");
                updateUiState();
                ensureRemoteAssistConnection();
            });
        }

        @Override
        public void onMessage(String type, String data) {
            runOnUiThread(() -> handleMessage(type, data));
        }

        @Override
        public void onFrame(byte[] jpegData) {
            long now = System.currentTimeMillis();
            if (now - lastFrameTime >= MIN_FRAME_INTERVAL) {
                lastFrameTime = now;
                runOnUiThread(() -> displayFrame(jpegData));
            } else {
                pendingFrame = jpegData;
                frameHandler.removeCallbacks(frameRenderer);
                frameHandler.postDelayed(frameRenderer, MIN_FRAME_INTERVAL);
            }
        }

        @Override
        public void onTouchEvent(float x, float y, String action) {
            // 家属端不接收触控事件。
        }

        @Override
        public void onError(String error) {
            runOnUiThread(() -> {
                isConnected = false;
                tvStatus.setText("连接错误：" + error);
                updateUiState();
                ensureRemoteAssistConnection();
            });
        }
    };

    private void handleMessage(String type, String data) {
        switch (type) {
            case "ack":
                break;
            case "connected":
            case "family_connected":
                isConnected = true;
                firstFrameAt = 0L;
                receivedFrameCount = 0L;
                lastFrameUiUpdateAt = 0L;
                tvStatus.setText("老人端已接通，正在接收画面");
                tvConnectionInfo.setText("连接状态：已建立");
                frameHandler.removeCallbacks(firstFrameTimeoutChecker);
                frameHandler.postDelayed(firstFrameTimeoutChecker, 5000);
                updateUiState();
                break;
            case "invite_sent":
                tvStatus.setText("邀请已发送，等待老人端确认");
                break;
            case "error":
                String code = data == null ? "" : data.trim();
                if ("elder_offline".equalsIgnoreCase(code)
                        && !targetElderId.isEmpty()
                        && !inviteAutoRetried) {
                    inviteAutoRetried = true;
                    tvStatus.setText("指定老人可能不在线，正在尝试自动匹配在线老人...");
                    tvConnectionInfo.setText("已触发自动匹配重试");
                    wsManager.sendInvite("family", myUserId, "");
                    return;
                }
                isConnected = false;
                tvStatus.setText("连接失败：" + mapErrorCode(code));
                frameHandler.removeCallbacks(firstFrameTimeoutChecker);
                updateUiState();
                break;
            case "touch_ack":
                tvConnectionInfo.setText("老人端已接收操作：" + data);
                break;
            case "hangup":
                isConnected = false;
                tvStatus.setText("会话已结束");
                tvConnectionInfo.setText("");
                frameHandler.removeCallbacks(firstFrameTimeoutChecker);
                updateUiState();
                break;
            default:
                break;
        }
    }

    private String mapErrorCode(String code) {
        String value = code == null ? "" : code.trim();
        if (value.isEmpty()) {
            return "未知错误";
        }
        switch (value) {
            case "elder_offline":
                return "老人端不在线";
            case "family_offline":
                return "家属端不在线";
            case "peer_not_connected":
                return "对端尚未建立连接";
            case "signaling_parse_failed":
                return "信令解析失败，请重新远程协助";
            default:
                return value;
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
        return "family_" + System.currentTimeMillis();
    }

    private void displayFrame(byte[] jpegData) {
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
            if (bmp != null) {
                long now = System.currentTimeMillis();
                if (firstFrameAt <= 0L) {
                    firstFrameAt = now;
                    frameHandler.removeCallbacks(firstFrameTimeoutChecker);
                }
                receivedFrameCount++;
                ivScreen.setImageBitmap(bmp);
                if (now - lastFrameUiUpdateAt >= 1000L) {
                    lastFrameUiUpdateAt = now;
                    tvConnectionInfo.setText("已接收画面帧：" + receivedFrameCount);
                }
            } else {
                tvConnectionInfo.setText("收到异常画面帧，正在继续重试...");
            }
        } catch (Exception e) {
            tvConnectionInfo.setText("画面解码失败：" + e.getMessage());
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        frameHandler.removeCallbacksAndMessages(null);
        if (wsManager != null) {
            wsManager.removeListener(wsListener);
            wsManager.disconnect();
        }
    }
}
