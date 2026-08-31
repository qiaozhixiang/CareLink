package com.carelink.app.ui.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.AuthApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.databinding.ActivityRoleSelectBinding;
import com.carelink.app.ui.elder.ElderMainActivity;
import com.carelink.app.ui.family.FamilyMainActivity;
import com.carelink.app.ui.family.JoinFamilyActivity;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;



/**
 * 身份选择页面 - 选角色后同步到后端
 */
@AndroidEntryPoint
public class RoleSelectActivity extends AppCompatActivity {


    private static final String TAG = "RoleSelectActivity";
    private ActivityRoleSelectBinding binding;
    private PreferenceManager preferenceManager;

    @Inject
    AuthApi authApi;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            binding = ActivityRoleSelectBinding.inflate(getLayoutInflater());
            if (binding == null || binding.getRoot() == null) {
                Log.e(TAG, "Binding inflate failed - binding or root is null");
                Toast.makeText(this, "页面初始化失败：布局加载错误", Toast.LENGTH_LONG).show();
                goToLogin();
                return;
            }
            setContentView(binding.getRoot());
            preferenceManager = new PreferenceManager(this);

            Log.d(TAG, "onCreate - isLoggedIn: " + preferenceManager.isLoggedIn());

            if (!preferenceManager.isLoggedIn()) {
                Log.d(TAG, "User is not logged in, redirecting to login");
                goToLogin();
                return;
            }

            String existingRole = normalizeRole(preferenceManager.getRole());

            Log.d(TAG, "onCreate - existingRole: '" + existingRole + "'");

            if (existingRole != null && !existingRole.isEmpty()) {
                Log.d(TAG, "Found existing role, navigating to: " + existingRole);
                navigateToRole(existingRole);
                return;
            }

            initView();

        } catch (Exception e) {
            Log.e(TAG, "onCreate 异常", e);
            Toast.makeText(this, "页面加载失败，请重新进入应用", Toast.LENGTH_LONG).show();
            goToLogin();
        }
    }


    private void initView() {
        binding.cardElder.setOnClickListener(v -> selectRole("ELDER"));
        binding.cardFamily.setOnClickListener(v -> selectRole("FAMILY"));
    }

    private void selectRole(String role) {

        try {
            Log.d(TAG, "selectRole called with role: " + role);

            if (role == null || role.isEmpty()) {
                Log.e(TAG, "Invalid role in selectRole");
                return;
            }

            preferenceManager.saveRole(role);
            preferenceManager.saveRoleSelectTime(System.currentTimeMillis());

            if (preferenceManager.getUserIdStr().isEmpty()) {
                preferenceManager.saveUserIdStr("local_" + System.currentTimeMillis());
            }

            String email = preferenceManager.getEmail();
            if (preferenceManager.getNickname().isEmpty() && email != null && !email.isEmpty()) {
                String[] parts = email.split("@");
                if (parts.length > 0) {
                    preferenceManager.saveNickname(parts[0]);
                }
            }

            // 异步同步角色到后端（不阻塞本地跳转）
            syncRoleToServer(role);

            navigateToRole(role);
        } catch (Exception e) {
            Log.e(TAG, "selectRole 异常", e);
            Toast.makeText(this, "选择身份失败，请稍后重试", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 将角色选择同步到后端，失败不影响本地使用
     */
    private void syncRoleToServer(String role) {
        try {
            if (authApi == null) {
                Log.w(TAG, "authApi 未注入，跳过后端同步");
                return;
            }
            Map<String, String> body = new HashMap<>();
            body.put("role", role);
            authApi.selectRole(body).enqueue(new Callback<BaseResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<BaseResponse<Map<String, Object>>> call,
                                       Response<BaseResponse<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        Log.d(TAG, "角色已同步到后端: " + role);
                    } else {
                        Log.w(TAG, "后端角色同步失败: " +
                                (response.body() != null ? response.body().getMessage() : "HTTP " + response.code()));
                    }
                }

                @Override
                public void onFailure(Call<BaseResponse<Map<String, Object>>> call, Throwable t) {
                    Log.w(TAG, "后端角色同步网络异常（不影响本地使用）", t);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "syncRoleToServer 异常", e);
        }
    }

    private void navigateToRole(String role) {
        try {
            String normalizedRole = normalizeRole(role);
            Log.d(TAG, "navigateToRole called with role: " + normalizedRole);

            if (normalizedRole == null) {
                Log.e(TAG, "Role is invalid in navigateToRole: " + role);
                Toast.makeText(this, "身份信息异常，请重新选择", Toast.LENGTH_LONG).show();
                return;
            }

            boolean needsFamilyBinding = shouldOpenJoinFamily(normalizedRole);
            Class<?> targetClass = needsFamilyBinding
                    ? JoinFamilyActivity.class
                    : ("ELDER".equals(normalizedRole) ? ElderMainActivity.class : FamilyMainActivity.class);
            Log.d(TAG, "Navigating to: " + targetClass.getSimpleName());

            Intent intent = new Intent(this, targetClass);

            String familyCode = null;
            try {
                familyCode = preferenceManager.getInviteCode();
            } catch (Throwable e) {
                Log.w(TAG, "获取家庭码失败", e);
            }
            if (familyCode != null && !familyCode.isEmpty()) {
                intent.setData(Uri.parse("carelink://family?code=" + familyCode));
            }

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Throwable e) {
            Log.e(TAG, "navigateToRole 最终兜底异常", e);
            Toast.makeText(this, "无法进入页面，请重试", Toast.LENGTH_LONG).show();
        }
    }

    private boolean shouldOpenJoinFamily(String normalizedRole) {
        long familyId = preferenceManager.getFamilyId();
        if (familyId > 0) {
            return false;
        }
        // 双端都允许先进入主页，再在“我的/家庭管理”中创建或加入家庭
        return false;
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



    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}

