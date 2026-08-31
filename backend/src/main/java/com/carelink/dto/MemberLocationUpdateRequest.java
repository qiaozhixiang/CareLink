package com.carelink.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MemberLocationUpdateRequest {
    @NotNull(message = "纬度不能为空")
    private Double latitude;

    @NotNull(message = "经度不能为空")
    private Double longitude;

    private String address;

    private Boolean enabled = true;

    private Long expireAt; // 可选，过期时间戳
}
