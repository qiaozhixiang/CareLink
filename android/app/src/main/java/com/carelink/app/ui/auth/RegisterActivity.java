package com.carelink.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.AuthApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.data.remote.dto.EmailCodeRequest;
import com.carelink.app.data.remote.dto.LoginResponse;
import com.carelink.app.data.remote.dto.RegisterRequest;
import com.carelink.app.databinding.ActivityRegisterBinding;
import com.carelink.app.utils.NetworkErrorHandler;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class RegisterActivity extends AppCompatActivity {

    private static final long VERIFY_CODE_COUNTDOWN_MS = 60_000L;

    private ActivityRegisterBinding binding;
    private PreferenceManager preferenceManager;
    private boolean isProcessing = false;
    private CountDownTimer verifyCodeCountDownTimer;

    @Inject
    AuthApi authApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            binding = ActivityRegisterBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            preferenceManager = new PreferenceManager(this);
            initView();
        } catch (Throwable e) {
            android.util.Log.e("RegisterActivity", "onCreate 异常", e);
            Toast.makeText(this, "页面加载失败，请重新进入", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initView() {
        updateSendCodeButtonState(true);
        binding.tvCodeHint.setText("点击右侧按钮发送，验证码 10 分钟内有效");

        binding.btnSendCode.setOnClickListener(v -> sendRegisterVerifyCode());

        binding.btnRegister.setOnClickListener(v -> {
            if (isProcessing) return;
            String email = getText(binding.etEmail);
            String verifyCode = getText(binding.etVerifyCode);
            String password = getText(binding.etPassword);

            if (email.isEmpty()) {
                binding.tilEmail.setError("请填写邮箱");
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.setError("邮箱格式不正确");
                return;
            }
            if (verifyCode.isEmpty() || verifyCode.length() != 6) {
                binding.tilVerifyCode.setError("请填写6位验证码");
                return;
            }
            if (password.isEmpty()) {
                binding.tilPassword.setError("请填写密码");
                return;
            }
            if (password.length() < 6) {
                binding.tilPassword.setError("密码至少6位");
                return;
            }

            binding.tilEmail.setError(null);
            binding.tilVerifyCode.setError(null);
            binding.tilPassword.setError(null);
            performServerRegister(email, password, verifyCode);
        });

        binding.tvLogin.setOnClickListener(v -> finish());
    }

    private void sendRegisterVerifyCode() {
        String email = getText(binding.etEmail);

        if (email.isEmpty()) {
            binding.tilEmail.setError("请先填写邮箱");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("邮箱格式不正确");
            return;
        }
        binding.tilEmail.setError(null);
        binding.tilVerifyCode.setError(null);
        updateSendCodeButtonState(false);
        binding.tvCodeHint.setText("正在发送验证码，请稍候...");

        authApi.sendEmailCode(new EmailCodeRequest(email, "register"))
                .enqueue(new Callback<BaseResponse<Void>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<Void>> call,
                                           Response<BaseResponse<Void>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            BaseResponse<Void> resp = response.body();
                            Toast.makeText(RegisterActivity.this,
                                    resp.getMessage(), Toast.LENGTH_SHORT).show();
                            if (resp.isSuccess()) {
                                startVerifyCodeCountDown();
                            } else {
                                binding.tvCodeHint.setText("发送失败，请检查邮箱后重新获取");
                                updateSendCodeButtonState(true);
                            }
                        } else {
                            binding.tvCodeHint.setText("发送失败，请稍后重试");
                            Toast.makeText(RegisterActivity.this,
                                    "发送失败，请稍后重试", Toast.LENGTH_SHORT).show();
                            updateSendCodeButtonState(true);
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                        NetworkErrorHandler.NetworkError err =
                                NetworkErrorHandler.handleFailure(RegisterActivity.this, t);
                        binding.tvCodeHint.setText("网络异常，请稍后重新发送");
                        Toast.makeText(RegisterActivity.this,
                                err.userMessage, Toast.LENGTH_SHORT).show();
                        updateSendCodeButtonState(true);
                    }
                });
    }

    private void startVerifyCodeCountDown() {
        if (verifyCodeCountDownTimer != null) {
            verifyCodeCountDownTimer.cancel();
        }
        verifyCodeCountDownTimer = new CountDownTimer(VERIFY_CODE_COUNTDOWN_MS, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = Math.max(1L, millisUntilFinished / 1000L);
                binding.btnSendCode.setEnabled(false);
                binding.btnSendCode.setText(seconds + "s后重发");
                binding.tvCodeHint.setText("验证码已发送，请查看邮箱（" + seconds + "s 后可重发）");
            }

            @Override
            public void onFinish() {
                verifyCodeCountDownTimer = null;
                binding.tvCodeHint.setText("未收到验证码？可重新发送");
                updateSendCodeButtonState(true);
            }
        };
        verifyCodeCountDownTimer.start();
    }

    private void updateSendCodeButtonState(boolean enabled) {
        binding.btnSendCode.setEnabled(enabled && !isProcessing);
        binding.btnSendCode.setAlpha(enabled && !isProcessing ? 1f : 0.7f);
        if (verifyCodeCountDownTimer == null) {
            binding.btnSendCode.setText("发送验证码");
        }
    }

    private void performServerRegister(String email, String password, String verifyCode) {
        isProcessing = true;
        setAuthActionEnabled(false);
        showLoading();

        preferenceManager.saveToken("");
        String nickname = buildRegisterNickname(email);
        RegisterRequest request = new RegisterRequest(email, password, nickname, verifyCode);
        authApi.register(request).enqueue(new Callback<BaseResponse<LoginResponse>>() {
            @Override
            public void onResponse(Call<BaseResponse<LoginResponse>> call,
                                   Response<BaseResponse<LoginResponse>> response) {
                isProcessing = false;
                hideLoading();
                setAuthActionEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<LoginResponse> resp = response.body();
                    if (resp.isSuccess() && resp.getData() != null) {
                        onRegisterSuccess(resp.getData());
                    } else {
                        Toast.makeText(RegisterActivity.this, resp.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    return;
                }

                NetworkErrorHandler.NetworkError err = NetworkErrorHandler.handleResponse(response);
                Toast.makeText(RegisterActivity.this, err.userMessage, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(Call<BaseResponse<LoginResponse>> call, Throwable t) {
                isProcessing = false;
                hideLoading();
                setAuthActionEnabled(true);
                NetworkErrorHandler.NetworkError err = NetworkErrorHandler.handleFailure(RegisterActivity.this, t);
                Toast.makeText(RegisterActivity.this, err.userMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onRegisterSuccess(LoginResponse data) {
        if (verifyCodeCountDownTimer != null) {
            verifyCodeCountDownTimer.cancel();
            verifyCodeCountDownTimer = null;
        }
        preferenceManager.saveToken("");
        preferenceManager.saveToken(data.getToken());
        preferenceManager.saveUserId(data.getUserId());
        preferenceManager.saveUserIdStr(String.valueOf(data.getUserId()));
        preferenceManager.saveEmail(data.getEmail() != null ? data.getEmail() : "");
        preferenceManager.saveNickname(data.getNickname() != null ? data.getNickname() : "");
        preferenceManager.saveRole("");

        Toast.makeText(this, "注册成功！请选择您的身份", Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, RoleSelectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

    }

    private void setAuthActionEnabled(boolean enabled) {
        binding.btnRegister.setEnabled(enabled);
        binding.tvLogin.setEnabled(enabled);
        updateSendCodeButtonState(enabled);
    }

    private String buildRegisterNickname(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "新用户";
        }
        String normalized = email.trim();
        int atIndex = normalized.indexOf('@');
        String prefix = atIndex > 0 ? normalized.substring(0, atIndex) : normalized;
        prefix = prefix.replaceAll("[^a-zA-Z0-9_\u4e00-\u9fa5]", "");
        if (prefix.isEmpty()) {
            return "新用户";
        }
        return prefix.length() > 12 ? prefix.substring(0, 12) : prefix;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (verifyCodeCountDownTimer != null) {
            verifyCodeCountDownTimer.cancel();
        }
    }
}

