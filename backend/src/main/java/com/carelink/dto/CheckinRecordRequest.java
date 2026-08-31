package com.carelink.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckinRecordRequest {
    @NotNull(message = "老人ID不能为空")
    private Long elderId;

    private Long taskId;

    private String title;

    private String status = "DONE"; // DONE / SKIP / MISSED

    private String note;
}
