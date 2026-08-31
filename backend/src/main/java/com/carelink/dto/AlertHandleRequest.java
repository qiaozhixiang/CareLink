package com.carelink.dto;

import lombok.Data;

@Data
public class AlertHandleRequest {
    private String status; // HANDLED 或 IGNORED
    private String handleNote;
}
