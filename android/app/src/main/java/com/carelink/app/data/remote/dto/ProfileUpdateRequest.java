package com.carelink.app.data.remote.dto;

public class ProfileUpdateRequest {
    private String nickname;
    private String avatarUrl;
    private String emergencyContactName;
    private String emergencyContactPhone;

    public ProfileUpdateRequest(String nickname, String avatarUrl) {
        this(nickname, avatarUrl, null, null);
    }

    public ProfileUpdateRequest(String nickname, String avatarUrl,
                                String emergencyContactName, String emergencyContactPhone) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
    }
}
