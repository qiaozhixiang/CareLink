package com.carelink.app.ui.auth;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.carelink.app.data.remote.api.AuthApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.data.remote.dto.EmailCodeRequest;
import com.carelink.app.data.remote.dto.ResetPasswordRequest;
import com.carelink.app.databinding.ActivityForgotPasswordBinding;
import com.carelink.app.utils.NetworkErrorHandler;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 忘记密码页 - 独立 Activity
 * 两步流程：
 *   Step1: 输入邮箱 → 发送验证码 → 自动显示第二步
 *   Step2: 输入验证码 + 新密码 + 确认密码 → 提交重置
 */
@AndroidEntryPoint
public class ForgotPasswordActivity extends AppCompatActivity {

    private static final long COUNTDOWN_MS = 60_000L;

    private ActivityForgotPasswordBinding binding;
    private boolean isProcessing = false;
    private int currentStep = 1;
    private CountDownTimer countDownTimer;

    @Inject
    AuthApi authApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
    }

    private void initView() {
        binding.btnSendCode.setOnClickListener(v -> sendVerifyCode());

        binding.btnSubmit.setOnClickListener(v -> {
            if (currentStep == 1) {
                goToStep2();
            } else {
                submitResetPassword();
            }
        });

        binding.tvBackToLogin.setOnClickListener(v -> finish());
    }

    // Step1: 发送验证码
    private void sendVerifyCode() {
        if (isProcessing) return;
        String email = getText(binding.etEmail);

        if (email.isEmpty()) {
            binding.tilEmail.setError("请输入邮箱");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("邮箱格式不正确");
            return;
        }
        binding.tilEmail.setError(null);

        isProcessing = true;
        binding.btnSendCode.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);

        authApi.sendResetCode(new EmailCodeRequest(email, "reset")).enqueue(new Callback<BaseResponse<Void>>() {
            @Override
            public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                isProcessing = false;
                binding.progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(ForgotPasswordActivity.this, "验证码已发送，请查收邮箱", Toast.LENGTH_SHORT).show();
                    goToStep2();
                } else {
                    binding.btnSendCode.setEnabled(true);
                    String msg = (response.body() != null) ? response.body().getMessage() : "发送失败";
                    Toast.makeText(ForgotPasswordActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                isProcessing = false;
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSendCode.setEnabled(true);
                NetworkErrorHandler.NetworkError err = NetworkErrorHandler.handleFailure(ForgotPasswordActivity.this, t);
                Toast.makeText(ForgotPasswordActivity.this, err.userMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    // 过渡到 Step2
    private void goToStep2() {
        String email = getText(binding.etEmail);
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("请输入正确的邮箱");
            return;
        }
        binding.tilEmail.setError(null);

        currentStep = 2;
        binding.tvStepHint.setText("请输入发送到 " + maskEmail(email) + " 的验证码");
        binding.tilEmail.setEnabled(false);
        binding.layoutVerifyCode.setVisibility(View.GONE);
        binding.tilNewPassword.setVisibility(View.VISIBLE);
        binding.tilConfirmPassword.setVisibility(View.VISIBLE);
        binding.btnSubmit.setText("重置密码");
        binding.progressBar.setVisibility(View.GONE);

        startCountDown();
    }

    // Step2: 提交重置
    private void submitResetPassword() {
        if (isProcessing) return;
        String email = getText(binding.etEmail);
        String code = getText(binding.etVerifyCode);
        String newPassword = getText(binding.etNewPassword);
        String confirmPassword = getText(binding.etConfirmPassword);

        if (code.isEmpty() || code.length() != 6) {
            binding.tilVerifyCode.setError("请输入6位验证码");
            return;
        }
        binding.tilVerifyCode.setError(null);

        if (newPassword.isEmpty()) {
            binding.tilNewPassword.setError("请输入新密码");
            return;
        }
        if (newPassword.length() < 6) {
            binding.tilNewPassword.setError("密码至少6位");
            return;
        }
        binding.tilNewPassword.setError(null);

        if (!newPassword.equals(confirmPassword)) {
            binding.tilConfirmPassword.setError("两次密码不一致");
            return;
        }
        binding.tilConfirmPassword.setError(null);

        isProcessing = true;
        setUiEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);

        ResetPasswordRequest request = new ResetPasswordRequest(email, code, newPassword);
        authApi.resetPassword(request).enqueue(new Callback<BaseResponse<Void>>() {
            @Override
            public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                isProcessing = false;
                binding.progressBar.setVisibility(View.GONE);
                setUiEnabled(true);

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(ForgotPasswordActivity.this, "密码重置成功，请使用新密码登录", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    String msg = (response.body() != null) ? response.body().getMessage() : "重置失败";
                    Toast.makeText(ForgotPasswordActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                isProcessing = false;
                binding.progressBar.setVisibility(View.GONE);
                setUiEnabled(true);
                NetworkErrorHandler.NetworkError err = NetworkErrorHandler.handleFailure(ForgotPasswordActivity.this, t);
                Toast.makeText(ForgotPasswordActivity.this, err.userMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startCountDown() {
        countDownTimer = new CountDownTimer(COUNTDOWN_MS, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = Math.max(1L, millisUntilFinished / 1000L);
                binding.btnSendCode.setEnabled(false);
                binding.btnSendCode.setText(seconds + "s后重发");
            }

            @Override
            public void onFinish() {
                countDownTimer = null;
                if (currentStep == 2) {
                    binding.btnSendCode.setEnabled(true);
                    binding.btnSendCode.setText("重新发送");
                }
            }
        };
        countDownTimer.start();
    }

    private void setUiEnabled(boolean enabled) {
        binding.btnSubmit.setEnabled(enabled);
        binding.etVerifyCode.setEnabled(enabled);
        binding.etNewPassword.setEnabled(enabled);
        binding.etConfirmPassword.setEnabled(enabled);
    }

    private String getText(android.widget.TextView tv) {
        return tv.getText().toString().trim();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        if (at <= 2) return email;
        return email.substring(0, 2) + "***" + email.substring(at);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }
}
