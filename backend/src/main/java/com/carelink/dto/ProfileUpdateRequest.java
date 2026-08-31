package com.carelink.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String nickname;
    private String avatarUrl;
    private String emergencyContactName;
    private String emergencyContactPhone;
}
