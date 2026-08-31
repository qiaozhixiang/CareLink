package com.carelink.app.data.remote.dto;

public class ResetPasswordRequest {
    private String email;
    private String verifyCode;
    private String newPassword;

    public ResetPasswordRequest(String email, String verifyCode, String newPassword) {
        this.email = email;
        this.verifyCode = verifyCode;
        this.newPassword = newPassword;
    }

    public String getEmail() { return email; }
    public String getVerifyCode() { return verifyCode; }
    public String getNewPassword() { return newPassword; }
}
