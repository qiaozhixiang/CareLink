package com.carelink.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberResponse {
    private Long userId;
    private String nickname;
    private String displayName;
    private String role;
    private String roleLabel;
    private String avatarUrl;
    private Boolean avatarShared;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private Long familyId;
    private String familyName;
    private Long creatorId;
    private Boolean creator;
    private Boolean currentUser;
    private Boolean joined;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
