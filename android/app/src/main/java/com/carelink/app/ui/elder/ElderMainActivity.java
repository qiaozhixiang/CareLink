package com.carelink.app.ui.elder;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import dagger.hilt.android.AndroidEntryPoint;


import com.carelink.app.R;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.remote.ElderRemoteAssistActivity;
import com.carelink.app.remote.WebSocketManager;
import com.carelink.app.ui.auth.LoginActivity;
import com.carelink.app.ui.profile.MyProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 老人端主界面 - 高德 SDK 异常不影响主界面加载 */
@AndroidEntryPoint
public class ElderMainActivity extends AppCompatActivity {



    private static final String TAG = "ElderMainActivity";
    private static final String REMOTE_INVITE_CHANNEL_ID = "elder_remote_invite_channel";
    private static final int REMOTE_INVITE_NOTIFICATION_ID = 32001;
    private Fragment currentFragment;
    private PreferenceManager preferenceManager;
    private boolean autoLocationRequested;
    private WebSocketManager inviteWsManager;
    private boolean isActivityForeground;
    private String inviteRegisterUserId = "";
    private String lastInviteFamilyUserId = "";
    private long lastInviteAtMs = 0L;
    private AlertDialog remoteInviteDialog;

    // 高德定位相关 — 用 Object 类型避免类加载时强依赖高德 SDK
    private Object locationClient;
    private Object locationOption;
    private Object locationListener;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fineGranted || coarseGranted) {
                    triggerAutoLocateOnMainOpen();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            preferenceManager = new PreferenceManager(this);
            if (!ensureAccess(preferenceManager, "ELDER")) {
                return;
            }
            initRemoteInviteListener();

            LinearLayout root = createRootLayout();
            FrameLayout container = createContentContainer();
            root.addView(container);

            BottomNavigationView bottomNav = createBottomNav();
            root.addView(bottomNav);
            setContentView(root);

            if (savedInstanceState == null) {
                try {
                    loadFragment(new ElderHomeFragment(), "home");
                    bottomNav.setSelectedItemId(R.id.nav_home);
                } catch (Throwable e) {
                    Log.e(TAG, "ElderHomeFragment 加载异常", e);
                    Toast.makeText(this, "首页加载失败", Toast.LENGTH_SHORT).show();
                }


                // 处理深度链接（家庭码远程打开指定页面）
                handleDeepLink(getIntent());
            }

            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    switchTab(new ElderHomeFragment(), "home");

                } else if (id == R.id.nav_task) {
                    switchTab(new TodayTaskFragment(), "task");
                } else if (id == R.id.nav_emergency) {
                    switchTab(new EmergencyFragment(), "emergency");
                } else if (id == R.id.nav_schedule) {
                    switchTab(new ScheduleFragment(), "schedule");
                } else if (id == R.id.nav_my) {
                    switchTab(new MyProfileFragment(), "my");
                } else {
                    Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show();
                }
                return true;
            });

            // 定位功能已迁移到 TodayTaskFragment（共享页）
            // triggerAutoLocateOnMainOpen();
        } catch (Exception e) {
            Log.e(TAG, "onCreate 异常", e);
            showFatalError(e);
        }
    }

    private boolean ensureAccess(PreferenceManager pm, String expectedRole) {
        if (!pm.isLoggedIn()) {
            goToLogin();
            return false;
        }
        String currentRole = normalizeRole(pm.getRole());
        if (currentRole == null) {
            goToRoleSelect();
            return false;
        }
        if (!expectedRole.equals(currentRole)) {
            redirectToRoleHome(currentRole);
            return false;
        }
        return true;
    }



    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if ("ELDER".equals(normalized) || "FAMILY".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private void redirectToRoleHome(String role) {
        Class<?> targetClass = "FAMILY".equals(role)
                ? com.carelink.app.ui.family.FamilyMainActivity.class
                : ElderMainActivity.class;
        Intent intent = new Intent(this, targetClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (getIntent() != null && getIntent().getData() != null) {
            intent.setData(getIntent().getData());
        }
        startActivity(intent);
        finish();
    }


    private LinearLayout createRootLayout() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        return root;
    }

    private FrameLayout createContentContainer() {
        FrameLayout container = new FrameLayout(this);
        container.setId(R.id.elder_content_container);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return container;
    }

    private BottomNavigationView createBottomNav() {
        BottomNavigationView bottomNav = new BottomNavigationView(this);
        bottomNav.setId(R.id.elder_bottom_nav);
        bottomNav.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
        bottomNav.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomNav.inflateMenu(R.menu.menu_elder_bottom);
        return bottomNav;
    }


    /**
     * 处理家庭码深度链接
     * URL 格式：carelink://family?code=123456&page=schedule
     *          https://yiyangjia.com/family?code=123456&page=schedule
     * 支持的 page 参数：home / task / emergency / schedule / my
     */
    private void handleDeepLink(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null) return;

        String code = data.getQueryParameter("code");
        String page = data.getQueryParameter("page");

        Log.d(TAG, "家庭码深度链接：code=" + code + ", page=" + page);

        // 存储家庭码（用于后续验证）
        if (code != null && !code.isEmpty()) {
            preferenceManager.saveInviteCode(code);
        }

        // 跳转到指定页面
        if (page != null && !page.isEmpty()) {
            navigateToPage(page);
        }
    }

    /**
     * 根据页面名称导航到对应 Tab
     */
    public void navigateToPage(String page) {
        BottomNavigationView bottomNav = findViewById(R.id.elder_bottom_nav);
        if (bottomNav == null) return;

        switch (page) {
            case "home":
                bottomNav.setSelectedItemId(R.id.nav_home);
                switchTab(new ElderHomeFragment(), "home");
                break;

            case "task":
            case "checkin":
                bottomNav.setSelectedItemId(R.id.nav_task);
                break;
            case "emergency":
                bottomNav.setSelectedItemId(R.id.nav_emergency);
                switchTab(new EmergencyFragment(), "emergency");
                break;
            case "schedule":
            case "calendar":
                bottomNav.setSelectedItemId(R.id.nav_schedule);
                break;
            case "my":
            case "settings":
                bottomNav.setSelectedItemId(R.id.nav_my);
                break;
            default:
                Log.w(TAG, "未知的深度链接页面: " + page);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 处理通过深度链接从外部再次唤起
        if (intent != null) {
            setIntent(intent);
            handleDeepLink(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityForeground = true;
        refreshRemoteInviteRegistration();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityForeground = false;
    }

    private void switchTab(Fragment fragment, String tag) {
        try {
            if (currentFragment != null && currentFragment.getClass().equals(fragment.getClass())) return;
            loadFragment(fragment, tag);
        } catch (Exception e) {
            Log.e(TAG, "切换页面失败: " + tag, e);
            Toast.makeText(this, "页面加载失败，请稍后重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadFragment(Fragment fragment, String tag) {
        try {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.elder_content_container, fragment, tag)
                    .commitAllowingStateLoss();
            currentFragment = fragment;
        } catch (Throwable e) {
            Log.e(TAG, "loadFragment 异常 tag=" + tag, e);
        }
    }


    private void triggerAutoLocateOnMainOpen() {
        if (autoLocationRequested || preferenceManager == null || !preferenceManager.isRealtimeLocationEnabled()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }
        autoLocationRequested = true;
        startSingleLocation();
    }

    /**
     * 高德定位 — 全部用 try-catch 保护，SDK 异常不影响主界面
     */
    private void startSingleLocation() {
        try {
            com.amap.api.location.AMapLocationClient.updatePrivacyShow(this, true, true);
            com.amap.api.location.AMapLocationClient.updatePrivacyAgree(this, true);

            com.amap.api.location.AMapLocationClient client =
                    new com.amap.api.location.AMapLocationClient(getApplicationContext());

            com.amap.api.location.AMapLocationClientOption option =
                    new com.amap.api.location.AMapLocationClientOption();
            option.setLocationMode(com.amap.api.location.AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            option.setOnceLocationLatest(true);
            option.setGpsFirst(true);
            option.setNeedAddress(true);
            option.setMockEnable(false);
            option.setWifiScan(true);
            option.setLocationCacheEnable(false);
            option.setHttpTimeOut(10000);

            client.setLocationListener(aMapLocation -> {
                handleLocationResult(aMapLocation);
            });

            client.stopLocation();
            client.setLocationOption(option);
            client.startLocation();

            this.locationClient = client;
        } catch (Throwable e) {
            Log.w(TAG, "高德定位初始化失败（不影响主界面使用）", e);
            autoLocationRequested = false;
        }
    }

    private void handleLocationResult(com.amap.api.location.AMapLocation aMapLocation) {
        try {
            if (preferenceManager == null) return;
            String now = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
            if (aMapLocation != null && aMapLocation.getErrorCode() == 0) {
                String address = aMapLocation.getPoiName();
                if (address == null || address.trim().isEmpty()) {
                    address = aMapLocation.getAddress();
                }
                if (address == null || address.trim().isEmpty()) {
                    address = "当前位置";
                }
                preferenceManager.saveShareStatus("单次共享");
                preferenceManager.saveShareLastLocation(address);
                preferenceManager.saveShareLastTime(now);
                preferenceManager.saveShareEndTime("本次共享完成后结束");
                preferenceManager.saveSharedLocationOwner("ELDER");
                preferenceManager.saveSharedSessionId("LOCAL-SHARE-SESSION");
                preferenceManager.setSharedLocationVisibleToBoth(true);
                preferenceManager.saveShareLatitude(aMapLocation.getLatitude());
                preferenceManager.saveShareLongitude(aMapLocation.getLongitude());
            }
        } catch (Throwable e) {
            Log.w(TAG, "处理定位结果异常", e);
        }
        autoLocationRequested = false;
        stopLocationSafely();
    }

    private void goToRoleSelect() {
        Intent intent = new Intent(this, com.carelink.app.ui.auth.RoleSelectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    private void showFatalError(Exception e) {
        LinearLayout errorLayout = new LinearLayout(this);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setGravity(Gravity.CENTER);
        errorLayout.setPadding(50, 100, 50, 50);
        TextView errorView = new TextView(this);
        errorView.setText("页面初始化异常，请重新进入应用");
        errorView.setTextSize(16);
        errorView.setGravity(Gravity.CENTER);
        errorLayout.addView(errorView);
        setContentView(errorLayout);
    }


    private void stopLocationSafely() {
        try {
            if (locationClient instanceof com.amap.api.location.AMapLocationClient) {
                ((com.amap.api.location.AMapLocationClient) locationClient).stopLocation();
            }
        } catch (Throwable ignored) {}
    }

    private void initRemoteInviteListener() {
        if (preferenceManager == null) {
            return;
        }
        inviteRegisterUserId = resolveCurrentUserIdForRemoteInvite();
        if (inviteRegisterUserId.isEmpty()) {
            return;
        }
        if (inviteWsManager != null) {
            return;
        }
        inviteWsManager = new WebSocketManager();
        inviteWsManager.addListener(inviteWsListener);
        inviteWsManager.connect();
    }

    private void refreshRemoteInviteRegistration() {
        if (inviteWsManager == null || inviteRegisterUserId.isEmpty()) {
            return;
        }
        if (inviteWsManager.isConnected()) {
            inviteWsManager.sendRegister("elder", inviteRegisterUserId);
        } else {
            inviteWsManager.connect();
        }
    }

    private void releaseRemoteInviteListener() {
        if (inviteWsManager != null) {
            inviteWsManager.removeListener(inviteWsListener);
            inviteWsManager.disconnect();
            inviteWsManager = null;
        }
    }

    private final WebSocketManager.WsListener inviteWsListener = new WebSocketManager.WsListener() {
        @Override
        public void onConnected() {
            if (inviteWsManager != null && !inviteRegisterUserId.isEmpty()) {
                inviteWsManager.sendRegister("elder", inviteRegisterUserId);
            }
        }

        @Override
        public void onDisconnected() {
            if (inviteWsManager == null) {
                return;
            }
            getWindow().getDecorView().postDelayed(() -> {
                if (inviteWsManager != null && !inviteWsManager.isConnected()) {
                    inviteWsManager.connect();
                }
            }, 3000);
        }

        @Override
        public void onMessage(String type, String data) {
            if ("incoming_invite".equals(type) || "family_starting".equals(type)) {
                String familyUserId = parseFamilyUserId(data);
                if (familyUserId.isEmpty()) {
                    return;
                }
                long now = System.currentTimeMillis();
                boolean duplicate = familyUserId.equals(lastInviteFamilyUserId)
                        && (now - lastInviteAtMs) < 1200;
                if (duplicate) {
                    return;
                }
                lastInviteFamilyUserId = familyUserId;
                lastInviteAtMs = now;
                runOnUiThread(() -> handleGlobalRemoteInvite(familyUserId));
            }
        }

        @Override
        public void onFrame(byte[] jpegData) {
            // 主页面不处理画面流。
        }

        @Override
        public void onTouchEvent(float x, float y, String action) {
            // 主页面不处理触控事件。
        }

        @Override
        public void onError(String error) {
            Log.w(TAG, "Global remote invite listener error: " + error);
        }
    };

    private String parseFamilyUserId(String data) {
        if (data == null) {
            return "";
        }
        String value = data.trim();
        if (value.startsWith("family:")) {
            value = value.substring("family:".length()).trim();
        }
        return value;
    }

    private void handleGlobalRemoteInvite(String familyUserId) {
        if (familyUserId == null || familyUserId.trim().isEmpty()) {
            return;
        }
        if (isActivityForeground && !ElderRemoteAssistActivity.isActive()) {
            boolean shown = showRemoteInviteDialog(familyUserId);
            if (shown) {
                return;
            }
        }
        boolean launched = launchRemoteAssistForInvite(familyUserId);
        if (!launched || !isActivityForeground) {
            showRemoteInviteNotification(familyUserId);
        }
    }

    private boolean showRemoteInviteDialog(String familyUserId) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return false;
        }
        try {
            if (remoteInviteDialog != null && remoteInviteDialog.isShowing()) {
                remoteInviteDialog.dismiss();
            }
            remoteInviteDialog = new AlertDialog.Builder(this)
                    .setTitle("收到远程协助请求")
                    .setMessage("家属“" + familyUserId + "”发起远程协助，是否现在处理？")
                    .setCancelable(false)
                    .setPositiveButton("立即处理", (dialog, which) -> launchRemoteAssistForInvite(familyUserId))
                    .setNegativeButton("稍后处理", (dialog, which) -> showRemoteInviteNotification(familyUserId))
                    .create();
            remoteInviteDialog.show();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Show remote invite dialog failed", e);
            return false;
        }
    }

    private boolean launchRemoteAssistForInvite(String familyUserId) {
        try {
            Intent intent = new Intent(this, ElderRemoteAssistActivity.class);
            intent.putExtra(ElderRemoteAssistActivity.EXTRA_AUTO_OPEN_INVITE, true);
            intent.putExtra(ElderRemoteAssistActivity.EXTRA_INVITE_FAMILY_ID, familyUserId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Launch remote assist from global invite failed", e);
            return false;
        }
    }

    private void showRemoteInviteNotification(String familyUserId) {
        ensureRemoteInviteChannel();
        Intent intent = new Intent(this, ElderRemoteAssistActivity.class);
        intent.putExtra(ElderRemoteAssistActivity.EXTRA_AUTO_OPEN_INVITE, true);
        intent.putExtra(ElderRemoteAssistActivity.EXTRA_INVITE_FAMILY_ID, familyUserId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                REMOTE_INVITE_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, REMOTE_INVITE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("家属请求远程协助")
                .setContentText("家属“" + familyUserId + "”发来共享协助请求，点击处理")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(REMOTE_INVITE_NOTIFICATION_ID, builder.build());
        }
    }

    private void ensureRemoteInviteChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                REMOTE_INVITE_CHANNEL_ID,
                "老人端远程协助邀请",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.enableVibration(true);
        nm.createNotificationChannel(channel);
    }

    private String resolveCurrentUserIdForRemoteInvite() {
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
        return "";
    }

    @Override
    protected void onDestroy() {
        try {
            if (remoteInviteDialog != null && remoteInviteDialog.isShowing()) {
                remoteInviteDialog.dismiss();
            }
            releaseRemoteInviteListener();
            if (locationClient instanceof com.amap.api.location.AMapLocationClient) {
                com.amap.api.location.AMapLocationClient client =
                        (com.amap.api.location.AMapLocationClient) locationClient;
                client.stopLocation();
                client.onDestroy();
            }
        } catch (Throwable e) {
            Log.w(TAG, "定位资源释放异常", e);
        }
        locationClient = null;
        super.onDestroy();
    }
}
