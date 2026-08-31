package com.carelink.app.ui.auth;

/** 登录页面 UI 状态 */
public class LoginUiState {
    public boolean loading = false;
    public boolean codeSent = false;
    public boolean loginSuccess = false;
    public boolean registerSuccess = false;  // 注册成功
    public boolean emailVerificationSent = false;  // 验证邮件已发送
    public boolean passwordResetSent = false;  // 密码重置邮件已发送
    public boolean needSelectRole = false;
    public String role = "";
    public String error = null;
    
    // 邮箱验证相关
    public boolean isEmailVerified = false;  // 邮箱是否已验证
    public boolean pendingEmailVerification = false;  // 等待邮箱验证
}