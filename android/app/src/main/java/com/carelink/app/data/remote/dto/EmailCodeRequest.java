package com.carelink.app.data.remote.dto;

public class EmailCodeRequest {
    private String email;
    private String scene;

    public EmailCodeRequest(String email, String scene) {
        this.email = email;
        this.scene = scene;
    }

    public String getEmail() { return email; }
    public String getScene() { return scene; }
}
