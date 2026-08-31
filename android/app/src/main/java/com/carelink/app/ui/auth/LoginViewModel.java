package com.carelink.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.carelink.app.base.BaseViewModel;
import com.carelink.app.data.local.pref.PreferenceManager;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * 登录 ViewModel
 * 保留类结构，避免工程内其他地方引用时编译失败。
 */
@HiltViewModel
public class LoginViewModel extends BaseViewModel {

    private final PreferenceManager preferenceManager;
    private final MutableLiveData<LoginUiState> uiState = new MutableLiveData<>();

    @Inject
    public LoginViewModel(PreferenceManager preferenceManager) {
        this.preferenceManager = preferenceManager;
        uiState.setValue(new LoginUiState());
    }

    public LiveData<LoginUiState> getUiState() {
        return uiState;
    }

    private boolean isValidEmail(String email) {
        return email != null && !email.trim().isEmpty()
                && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPassword(String password) {
        return password != null && password.length() >= 6;
    }

    public void loginWithEmail(String email, String password) {
        if (!isValidEmail(email)) {
            LoginUiState s = new LoginUiState();
            s.error = "请输入有效的邮箱地址";
            uiState.setValue(s);
            return;
        }
        if (!isValidPassword(password)) {
            LoginUiState s = new LoginUiState();
            s.error = "请输入密码（至少6位）";
            uiState.setValue(s);
            return;
        }

        LoginUiState loading = new LoginUiState();
        loading.loading = true;
        uiState.setValue(loading);

        String savedEmail = preferenceManager.getEmail();
        String savedPassword = getStoredPassword();
        LoginUiState s = new LoginUiState();


        if (savedEmail.isEmpty()) {
            s.error = "该邮箱未注册，请先注册";
        } else if (!savedEmail.equalsIgnoreCase(email)) {
            s.error = "邮箱不存在";
        } else if (!savedPassword.equals(password)) {
            s.error = "密码错误";
        } else {
            preferenceManager.saveUserIdStr("local_" + Math.abs(email.hashCode()));
            if (preferenceManager.getNickname().isEmpty()) {
                preferenceManager.saveNickname(email.split("@")[0]);
            }
            s.loginSuccess = true;
            s.needSelectRole = preferenceManager.getRole() == null || preferenceManager.getRole().isEmpty();
            s.role = preferenceManager.getRole();
        }
        uiState.setValue(s);
    }

    public void registerWithEmail(String email, String password) {
        if (!isValidEmail(email)) {
            LoginUiState s = new LoginUiState();
            s.error = "请输入有效的邮箱地址";
            uiState.setValue(s);
            return;
        }
        if (!isValidPassword(password)) {
            LoginUiState s = new LoginUiState();
            s.error = "密码至少需要6位字符";
            uiState.setValue(s);
            return;
        }

        LoginUiState s = new LoginUiState();
        if (!preferenceManager.getEmail().isEmpty() && preferenceManager.getEmail().equalsIgnoreCase(email)) {
            s.error = "该邮箱已被注册，请直接登录";
        } else {
            preferenceManager.saveEmail(email);
            preferenceManager.saveLocalPassword(password);
            preferenceManager.saveToken("");
            preferenceManager.saveUserIdStr("local_" + Math.abs(email.hashCode()));
            preferenceManager.saveNickname(email.split("@")[0]);
            s.registerSuccess = true;
            s.emailVerificationSent = false;
            s.pendingEmailVerification = false;

        }
        uiState.setValue(s);
    }

    public void checkCurrentUser() {
        if (preferenceManager.isLoggedIn()) {
            LoginUiState s = new LoginUiState();
            s.loginSuccess = true;
            s.needSelectRole = preferenceManager.getRole() == null || preferenceManager.getRole().isEmpty();
            s.role = preferenceManager.getRole();
            uiState.setValue(s);
        }

    }

    public void checkEmailVerificationStatus() {
        LoginUiState s = new LoginUiState();
        s.isEmailVerified = true;
        s.error = "当前账号无需额外邮箱验证";
        uiState.setValue(s);
    }

    public void resendVerificationEmail() {
        LoginUiState s = new LoginUiState();
        s.error = "当前账号暂不需要重新发送验证邮件";
        uiState.setValue(s);
    }

    public void resendVerificationEmailByEmail(String email) {
        LoginUiState s = new LoginUiState();
        s.error = "当前账号暂不需要重新发送验证邮件";
        uiState.setValue(s);
    }

    public void resetPassword(String email) {
        LoginUiState s = new LoginUiState();
        if (!isValidEmail(email)) {
            s.error = "请输入有效的邮箱地址";
        } else if (!email.equalsIgnoreCase(preferenceManager.getEmail())) {
            s.error = "该邮箱未注册";
        } else {
            s.passwordResetSent = true;
        }
        uiState.setValue(s);
    }

    private String getStoredPassword() {
        String password = preferenceManager.getLocalPassword();
        if (!password.isEmpty()) {
            return password;
        }
        String legacyPassword = preferenceManager.getToken();
        if (!legacyPassword.isEmpty()) {
            preferenceManager.saveLocalPassword(legacyPassword);
            preferenceManager.saveToken("");
        }
        return preferenceManager.getLocalPassword();
    }
}

