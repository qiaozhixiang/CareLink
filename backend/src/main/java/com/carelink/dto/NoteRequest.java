package com.carelink.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoteRequest {
    @NotNull(message = "老人ID不能为空")
    private Long elderId;

    private String content;

    private String tags;

    private Integer isImportant = 0;

    private String imageUrl;
}
