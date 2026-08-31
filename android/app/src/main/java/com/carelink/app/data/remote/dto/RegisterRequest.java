package com.carelink.app.data.remote.dto;

public class RegisterRequest {
    private String email;
    private String password;
    private String nickname;
    private String verifyCode;

    public RegisterRequest(String email, String password, String nickname, String verifyCode) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.verifyCode = verifyCode;
    }

    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }
    public String getVerifyCode() { return verifyCode; }
}

