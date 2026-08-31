package com.carelink.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long userId;
    private String email;
    private String nickname;
    private String avatarUrl;
    private String role;
    private Long familyId;
    private Boolean emailVerified;
}
