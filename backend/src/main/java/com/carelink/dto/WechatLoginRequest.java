package com.carelink.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录请求
 * code: 微信授权码，由 App 端通过微信 SDK 获取
 */
@Data
public class WechatLoginRequest {
    @NotBlank(message = "微信授权码不能为空")
    private String code;

    /** 昵称（首次微信登录时使用） */
    private String nickname;
}
