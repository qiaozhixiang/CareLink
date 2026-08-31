package com.carelink.app.ui.family;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import dagger.hilt.android.AndroidEntryPoint;


import com.carelink.app.R;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.ui.auth.LoginActivity;
import com.carelink.app.ui.family.FamilyAiChatFragment;
import com.carelink.app.ui.profile.MyProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** 家属端主界面 */
@AndroidEntryPoint
public class FamilyMainActivity extends AppCompatActivity {


    private static final String TAG = "FamilyMainActivity";
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            PreferenceManager pm = new PreferenceManager(this);
            if (!ensureAccess(pm, "FAMILY")) {
                return;
            }

            LinearLayout root = createRootLayout();
            FrameLayout container = createContentContainer();
            root.addView(container);

            BottomNavigationView bottomNav = createBottomNav();
            root.addView(bottomNav);
            setContentView(root);

            if (savedInstanceState == null) {
                try {
                    loadInitialFragment();
                } catch (Throwable e) {
                    Log.e(TAG, "loadInitialFragment 异常", e);
                    Toast.makeText(this, "页面加载异常，已切换", Toast.LENGTH_SHORT).show();
                }
                handleIntentExtras(getIntent());
                handleDeepLink(getIntent());
            }

            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_dashboard) {
                    switchTab(new DashboardFragment(), "dashboard");
                } else if (id == R.id.nav_companion) {
                    switchTab(new CompanionFragment(), "companion");
                } else if (id == R.id.nav_share) {
                    switchTab(new FamilyShareFragment(), "share");
                } else if (id == R.id.nav_ai) {
                    switchTab(new FamilyAiChatFragment(), "ai");
                } else if (id == R.id.nav_my) {
                    switchTab(new MyProfileFragment(), "my");
                } else {
                    Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
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
        String normalized = role.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if ("ELDER".equals(normalized) || "FAMILY".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private void redirectToRoleHome(String role) {
        Class<?> targetClass = "ELDER".equals(role)
                ? com.carelink.app.ui.elder.ElderMainActivity.class
                : FamilyMainActivity.class;
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
        container.setId(R.id.family_content_container);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return container;
    }

    private BottomNavigationView createBottomNav() {
        BottomNavigationView bottomNav = new BottomNavigationView(this);
        bottomNav.setId(R.id.family_bottom_nav);
        bottomNav.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
        bottomNav.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomNav.inflateMenu(R.menu.menu_family_bottom);
        return bottomNav;
    }

    private void loadInitialFragment() {
        switchTab(new DashboardFragment(), "dashboard");
        BottomNavigationView bottomNav = findViewById(R.id.family_bottom_nav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }
    }

    private void switchToDashboard() {
        switchTab(new DashboardFragment(), "dashboard");
    }

    private void switchToCompanion() {
        switchTab(new CompanionFragment(), "companion");
    }

    private void switchToShare() {
        switchTab(new FamilyShareFragment(), "share");
    }

    private void switchToAi() {
        switchTab(new FamilyAiChatFragment(), "ai");
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null) return;

        String code = data.getQueryParameter("code");
        String page = data.getQueryParameter("page");

        Log.d(TAG, "家庭码深度链接：code=" + code + ", page=" + page);

        if (code != null && !code.isEmpty()) {
            new PreferenceManager(this).saveInviteCode(code);
        }
        if (page != null && !page.isEmpty()) {
            navigateToPage(page);
        }
    }

    public void navigateToPage(String page) {
        BottomNavigationView bottomNav = findViewById(R.id.family_bottom_nav);
        if (bottomNav == null) return;

        switch (page) {
            case "dashboard":
            case "summary":
                bottomNav.setSelectedItemId(R.id.nav_dashboard);
                switchToDashboard();
                break;
            case "companion":
                bottomNav.setSelectedItemId(R.id.nav_companion);
                switchToCompanion();
                break;
            case "map":
            case "share":
                bottomNav.setSelectedItemId(R.id.nav_share);
                switchToShare();
                break;
            case "ai":
            case "chat":
                bottomNav.setSelectedItemId(R.id.nav_ai);
                switchToAi();
                break;
            case "members":
                switchTab(new MemberFragment(), "members");
                break;
            case "my":
            case "settings":
                bottomNav.setSelectedItemId(R.id.nav_my);
                switchTab(new MyProfileFragment(), "my");
                break;
            default:
                Log.w(TAG, "未知的深度链接页面: " + page);
        }
    }

    private void handleIntentExtras(Intent intent) {
        if (intent == null) {
            return;
        }
        String openPage = intent.getStringExtra("open_page");
        if ("members".equalsIgnoreCase(openPage)) {
            switchTab(new MemberFragment(), "members");
        } else if ("map".equalsIgnoreCase(openPage) || "share".equalsIgnoreCase(openPage)) {
            BottomNavigationView bottomNav = findViewById(R.id.family_bottom_nav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.nav_share);
            }
            switchToShare();
        } else if ("companion".equalsIgnoreCase(openPage)) {
            BottomNavigationView bottomNav = findViewById(R.id.family_bottom_nav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.nav_companion);
            }
            switchToCompanion();
        } else if ("ai".equalsIgnoreCase(openPage) || "chat".equalsIgnoreCase(openPage)) {
            BottomNavigationView bottomNav = findViewById(R.id.family_bottom_nav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.nav_ai);
            }
            switchToAi();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            setIntent(intent);
            handleIntentExtras(intent);
            handleDeepLink(intent);
        }
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
                    .replace(R.id.family_content_container, fragment, tag)
                    .commitAllowingStateLoss();
            currentFragment = fragment;
        } catch (Throwable e) {
            Log.e(TAG, "loadFragment 异常 tag=" + tag, e);
        }
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
}




