package com.carelink.app.ui.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.AuthApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.data.remote.dto.LoginRequest;
import com.carelink.app.data.remote.dto.LoginResponse;
import com.carelink.app.databinding.ActivityLoginBinding;
import com.carelink.app.ui.elder.ElderMainActivity;
import com.carelink.app.ui.family.FamilyMainActivity;
import com.carelink.app.ui.family.JoinFamilyActivity;

import com.carelink.app.utils.NetworkErrorHandler;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.Locale;




/**
 * 登录页 - 正式上线版
 * 接入统一网络错误处理，所有网络异常通过 NetworkErrorHandler 统一解析
 */
@AndroidEntryPoint
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private ActivityLoginBinding binding;
    private PreferenceManager preferenceManager;
    private boolean isProcessing = false;
    private long lastBackPressedAt = 0L;
    // 存储深度链接中的家庭码和目标页面
    private String pendingFamilyCode;
    private String pendingPage;

    @Inject
    AuthApi authApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            binding = ActivityLoginBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            preferenceManager = new PreferenceManager(this);

            registerBackHandler();
            initView();
            handleDeepLink(getIntent());  // 处理家庭码唤起
            checkSessionExpiredHint();
            checkCurrentUser();
        } catch (Throwable e) {
            Log.e(TAG, "onCreate 异常", e);
            Toast.makeText(this, "页面加载失败，请重新进入应用", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void checkSessionExpiredHint() {
        Intent intent = getIntent();
        if (intent == null || !intent.getBooleanExtra("session_expired", false)) {
            return;
        }
        String message = intent.getStringExtra("session_expired_message");
        Toast.makeText(this,
                message == null || message.trim().isEmpty() ? "登录已过期，请重新登录" : message,
                Toast.LENGTH_LONG).show();
        intent.removeExtra("session_expired");
        intent.removeExtra("session_expired_message");
    }



    private void initView() {
        // 邮箱登录按钮

        binding.btnLogin.setOnClickListener(v -> {
            if (isProcessing) return;
            String email = getText(binding.etEmail);
            String password = getText(binding.etPassword);


            // 输入校验
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请填写邮箱和密码", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.setError("邮箱格式不正确");
                return;
            }
            binding.tilEmail.setError(null);
            performServerLogin(email, password);
        });

        // 注册入口跳转独立注册页
        binding.tvRegister.setOnClickListener(v -> {
            if (isProcessing) return;
            startActivity(new Intent(this, RegisterActivity.class));
        });

        // 忘记密码 - 跳转到独立页面
        binding.tvForgotPassword.setOnClickListener(v -> {
            if (isProcessing) return;
            startActivity(new Intent(this, ForgotPasswordActivity.class));
        });
    }



    /** 检查本地是否已登录，有角色则直接跳转 */
    private void checkCurrentUser() {
        String savedRole = normalizeRole(preferenceManager.getRole());

        if (preferenceManager.isLoggedIn() && savedRole != null) {
            Log.d(TAG, "已登录且有角色: " + savedRole + "，直接跳转主页");
            navigateToMain(savedRole);
            return;
        }
        if (preferenceManager.isLoggedIn()) {
            navigateToRoleSelect();
            return;
        }
        Log.d(TAG, "未登录，显示登录页");
    }


    // ────────────────────────────────────────────────────────────
    // 服务器邮箱+密码登录（接入统一错误处理）
    // ────────────────────────────────────────────────────────────
    private void performServerLogin(String email, String password) {
        isProcessing = true;
        setAuthActionEnabled(false);
        showLoading();

        LoginRequest request = new LoginRequest(email, password);
        authApi.login(request).enqueue(new Callback<BaseResponse<LoginResponse>>() {
            @Override
            public void onResponse(Call<BaseResponse<LoginResponse>> call,
                                   Response<BaseResponse<LoginResponse>> response) {
                isProcessing = false;
                hideLoading();
                setAuthActionEnabled(true);

                // 业务层错误：HTTP 200 但 code != 200
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<LoginResponse> resp = response.body();
                    if (resp.isSuccess() && resp.getData() != null) {
                        onLoginSuccess(resp.getData());
                    } else {
                        NetworkErrorHandler.NetworkError err =
                                NetworkErrorHandler.handleBusinessError(resp.getMessage(), response.code());
                        handleLoginError(err);
                    }
                    return;
                }

                // HTTP 层面错误（4xx/5xx）
                NetworkErrorHandler.NetworkError err =
                        NetworkErrorHandler.handleResponse(response);
                handleLoginError(err);
            }

            @Override
            public void onFailure(Call<BaseResponse<LoginResponse>> call, Throwable t) {
                isProcessing = false;
                hideLoading();
                setAuthActionEnabled(true);
                Log.e(TAG, "登录网络异常", t);
                NetworkErrorHandler.NetworkError err =
                        NetworkErrorHandler.handleFailure(LoginActivity.this, t);
                handleLoginError(err);
            }
        });
    }


    private void handleLoginError(NetworkErrorHandler.NetworkError err) {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        // 401 特殊处理：Token 过期，清本地状态并引导重新登录
        if (err.type == NetworkErrorHandler.ErrorType.AUTH_ERROR) {
            preferenceManager.redirectToLogin(this, err.userMessage);
            return;
        }
        Toast.makeText(this, err.userMessage, Toast.LENGTH_LONG).show();
    }

    private void handleRegisterError(NetworkErrorHandler.NetworkError err) {
        // 注册场景不应该出现"登录已过期"，改为友好提示
        if (err.type == NetworkErrorHandler.ErrorType.AUTH_ERROR) {
            Toast.makeText(this, "注册失败，请检查网络或稍后重试", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, err.userMessage, Toast.LENGTH_LONG).show();
    }

    // ────────────────────────────────────────────────────────────
    // 登录成功后续
    // ────────────────────────────────────────────────────────────
    private void onLoginSuccess(LoginResponse data) {
        preferenceManager.saveToken(data.getToken());
        preferenceManager.saveUserId(data.getUserId());
        preferenceManager.saveUserIdStr(String.valueOf(data.getUserId()));
        preferenceManager.saveEmail(data.getEmail() != null ? data.getEmail() : "");
        preferenceManager.saveNickname(data.getNickname() != null ? data.getNickname() : "");
        if (data.getRole() != null) {
            preferenceManager.saveRole(data.getRole());
        }
        if (data.getFamilyId() != null) {
            preferenceManager.saveFamilyId(data.getFamilyId());
        }

        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();

        String role = data.getRole();
        if (role != null && !role.isEmpty()) {
            navigateToMain(role);
        } else {
            navigateToRoleSelect();
        }

    }

    private void setAuthActionEnabled(boolean enabled) {
        binding.btnLogin.setEnabled(enabled);
        binding.tvRegister.setEnabled(enabled);
    }



    private String getText(android.widget.TextView tv) {
        return tv.getText().toString().trim();
    }



    private void showLoading() {
        binding.progressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        binding.progressBar.setVisibility(View.GONE);
    }

    private void registerBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                long now = System.currentTimeMillis();
                if (now - lastBackPressedAt < 1500) {
                    finish();
                } else {
                    lastBackPressedAt = now;
                    Toast.makeText(LoginActivity.this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * 处理家庭码深度链接（URL 唤起 App）
     * carelink://family?code=123456&page=schedule
     */
    private void handleDeepLink(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null) return;

        pendingFamilyCode = data.getQueryParameter("code");
        pendingPage = data.getQueryParameter("page");
        Log.d(TAG, "家庭码唤起：code=" + pendingFamilyCode + ", page=" + pendingPage);

        // 存储家庭码供后续使用
        if (pendingFamilyCode != null && !pendingFamilyCode.isEmpty()) {
            preferenceManager.saveInviteCode(pendingFamilyCode);
        }
    }

    private void navigateToRoleSelect() {
        Intent intent = new Intent(this, RoleSelectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        attachDeepLink(intent);
        startActivity(intent);
        finish();
    }

    private void navigateToMain(String role) {
        String normalizedRole = normalizeRole(role);
        if (normalizedRole == null) {
            Log.w(TAG, "未知角色，跳转身份选择页: " + role);
            navigateToRoleSelect();
            return;
        }
        Class<?> target = shouldOpenJoinFamily(normalizedRole)
                ? JoinFamilyActivity.class
                : ("ELDER".equals(normalizedRole)
                ? ElderMainActivity.class
                : FamilyMainActivity.class);
        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        attachDeepLink(intent);
        startActivity(intent);
        finish();
    }

    private boolean shouldOpenJoinFamily(String normalizedRole) {
        long familyId = preferenceManager.getFamilyId();
        if (familyId > 0) {
            return false;
        }
        // 老人端不强制要求先加入家庭，可直接进入主页使用
        if ("ELDER".equals(normalizedRole)) {
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


    /** 将深度链接参数传递给目标 Activity */
    private void attachDeepLink(Intent intent) {
        if (pendingFamilyCode != null) {
            intent.setData(Uri.parse("carelink://family?code=" + pendingFamilyCode
                    + (pendingPage != null ? "&page=" + pendingPage : "")));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            setIntent(intent);
            handleDeepLink(intent);
        }
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}

