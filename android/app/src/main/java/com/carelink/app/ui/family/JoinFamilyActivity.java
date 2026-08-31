package com.carelink.app.ui.family;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.FamilyRepository;
import com.carelink.app.databinding.ActivityJoinFamilyBinding;
import com.carelink.app.ui.elder.ElderMainActivity;
import com.carelink.app.ui.family.FamilyMainActivity;
import com.carelink.app.utils.NetworkErrorHandler;

import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * 家庭绑定页：使用邀请码加入家庭 或 创建新家庭
 * 登录后、进入主界面之前调用
 */
@AndroidEntryPoint
public class JoinFamilyActivity extends AppCompatActivity {

    private static final String TAG = "JoinFamilyActivity";

    private ActivityJoinFamilyBinding binding;
    private PreferenceManager preferenceManager;
    private boolean isProcessing = false;

    @Inject
    FamilyRepository familyRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityJoinFamilyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferenceManager = new PreferenceManager(this);
        fillPendingInviteCode();

        binding.btnJoinFamily.setOnClickListener(v -> {
            if (isProcessing) return;
            String code = binding.etInviteCode.getText().toString().trim();
            if (code.isEmpty()) {
                binding.tilInviteCode.setError("请输入邀请码");
                return;
            }
            if (code.length() != 6) {
                binding.tilInviteCode.setError("邀请码为6位数字");
                return;
            }
            binding.tilInviteCode.setError(null);
            validateAndJoinFamily(code);
        });

        binding.btnCreateFamily.setOnClickListener(v -> {
            if (isProcessing) return;
            String name = binding.etFamilyName.getText().toString().trim();
            String familyNameError = validateFamilyName(name);
            if (familyNameError != null) {
                binding.tilFamilyName.setError(familyNameError);
                return;
            }
            binding.tilFamilyName.setError(null);
            createFamily(name);
        });

        binding.btnSkip.setOnClickListener(v -> navigateToMain());
    }

    private void fillPendingInviteCode() {
        String pendingCode = preferenceManager.getInviteCode();
        if (pendingCode != null && !pendingCode.trim().isEmpty()) {
            binding.etInviteCode.setText(pendingCode.trim());
            binding.tvStatus.setText("已为您自动填入邀请码，可直接加入家庭");
            binding.tvStatus.setVisibility(View.VISIBLE);
        }
    }

    private void validateAndJoinFamily(String code) {
        startProcessing("正在校验邀请码...");
        familyRepository.validateInviteCode(code, new FamilyRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                binding.tvStatus.setText("邀请码有效，正在加入家庭...");
                joinFamily(code);
            }

            @Override
            public void onError(String message) {
                finishProcessing();
                showErrorMessage(message == null || message.trim().isEmpty() ? "邀请码无效或已过期" : message);
            }
        });
    }

    private void joinFamily(String code) {
        familyRepository.joinFamily(code, new FamilyRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                long familyId = preferenceManager.getFamilyId();
                if (familyId > 0) {
                    binding.tvStatus.setText("加入成功，正在同步家庭信息...");
                    syncFamilyInfoAndFinish(familyId, "加入家庭成功");
                } else {
                    finishProcessing();
                    showSuccessAndNavigate("加入家庭成功");
                }
            }

            @Override
            public void onError(String message) {
                finishProcessing();
                showErrorMessage(message);
            }
        });
    }

    private String validateFamilyName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "请输入家庭名称";
        }
        String normalized = name.trim();
        if (normalized.length() > 20) {
            return "家庭名称最多 20 个字";
        }
        return null;
    }

    private void createFamily(String name) {
        String normalizedName = name == null ? "" : name.trim();
        startProcessing("正在创建家庭...");
        familyRepository.createFamily(normalizedName, new FamilyRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                long familyId = preferenceManager.getFamilyId();
                if (familyId > 0) {
                    binding.tvStatus.setText("家庭已创建，正在同步邀请码...");
                    syncFamilyInfoAndFinish(familyId, buildCreateSuccessMessage());
                } else {
                    finishProcessing();
                    showSuccessAndNavigate(buildCreateSuccessMessage());
                }
            }

            @Override
            public void onError(String message) {
                finishProcessing();
                String finalMessage = message;
                if (finalMessage != null && finalMessage.contains("服务器可能暂时不可用")) {
                    finalMessage = "服务器暂时不可用，请稍后重试";
                }
                showErrorMessage(finalMessage);
            }
        });
    }






    private void syncFamilyInfoAndFinish(long familyId, String successMessage) {
        familyRepository.getFamilyInfo(familyId, new FamilyRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                finishProcessing();
                showSuccessAndNavigate(successMessage);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "syncFamilyInfoAndFinish fallback: " + message);
                finishProcessing();
                showSuccessAndNavigate(successMessage);
            }
        });
    }




    private String buildCreateSuccessMessage() {
        String inviteCode = preferenceManager.getInviteCode();
        if (inviteCode != null && !inviteCode.trim().isEmpty()) {
            return "家庭创建成功，邀请码：" + inviteCode.trim();
        }
        return "家庭创建成功，请在家庭设置中查看邀请码";
    }

    private void startProcessing(String statusText) {
        isProcessing = true;
        setActionEnabled(false);
        showLoading(true, statusText);
    }

    private void finishProcessing() {
        isProcessing = false;
        setActionEnabled(true);
        showLoading(false, "");
    }

    private void showSuccessAndNavigate(String message) {
        binding.tvStatus.setText(message);
        binding.tvStatus.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        navigateToMain();
    }

    private void showErrorMessage(String message) {
        String finalMessage = message == null || message.trim().isEmpty()
                ? "操作失败，请稍后重试"
                : message.trim();
        binding.tvStatus.setText(finalMessage);
        binding.tvStatus.setVisibility(View.VISIBLE);
        Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
    }

    private void handleError(NetworkErrorHandler.NetworkError err) {
        if (err == null) {
            showErrorMessage("操作失败，请重试");
            return;
        }
        if (err.type == NetworkErrorHandler.ErrorType.NETWORK_UNAVAILABLE) {
            showErrorMessage("当前无网络，请检查网络后重试");
        } else if (err.type == NetworkErrorHandler.ErrorType.TIMEOUT) {
            showErrorMessage("网络超时，请稍后重试");
        } else {
            showErrorMessage(err.userMessage);
        }
    }

    private void navigateToMain() {
        String role = normalizeRole(preferenceManager.getRole());
        if (role == null) {
            Intent intent = new Intent(this, com.carelink.app.ui.auth.RoleSelectActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        Class<?> target = "ELDER".equals(role)
                ? ElderMainActivity.class
                : FamilyMainActivity.class;
        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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


    private void showLoading(boolean show, String statusText) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.tvStatus.setText(statusText == null ? "" : statusText);
        binding.tvStatus.setVisibility(show || (statusText != null && !statusText.isEmpty()) ? View.VISIBLE : View.GONE);
    }

    private void setActionEnabled(boolean enabled) {
        binding.btnJoinFamily.setEnabled(enabled);
        binding.btnCreateFamily.setEnabled(enabled);
        binding.btnSkip.setEnabled(enabled);
        binding.etInviteCode.setEnabled(enabled);
        binding.etFamilyName.setEnabled(enabled);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}

