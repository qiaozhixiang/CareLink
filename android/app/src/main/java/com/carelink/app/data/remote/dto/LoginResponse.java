package com.carelink.app.data.remote.dto;

public class LoginResponse {
    private String token;
    private long userId;
    private String email;
    private String nickname;
    private String avatarUrl;
    private String role;
    private Long familyId;
    private Boolean emailVerified;

    public String getToken() { return token; }
    public long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getRole() { return role; }
    public Long getFamilyId() { return familyId; }
    public Boolean getEmailVerified() { return emailVerified; }

    public boolean needSelectRole() {
        return role == null || role.isEmpty();
    }
}
