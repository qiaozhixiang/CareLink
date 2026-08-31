package com.carelink.app.data.remote.dto;

public class LoginRequest {
    /** 邮箱登录 */
    private String email;
    private String password;

    /** 无参构造器（Retrofit/Gson 反序列化需要） */
    public LoginRequest() {}

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() { return email; }
    public String getPassword() { return password; }
}

