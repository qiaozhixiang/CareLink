package com.carelink.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleSelectRequest {
    @NotBlank(message = "角色不能为空")
    private String role; // ELDER 或 FAMILY
}
