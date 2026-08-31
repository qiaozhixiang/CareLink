package com.carelink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppointmentRequest {
    @NotNull(message = "老人ID不能为空")
    private Long elderId;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String category = "CUSTOM";

    @NotNull(message = "开始时间不能为空")
    private Long startTime; // 时间戳毫秒

    private Long endTime;

    private String location;

    private String notes;

    private String reminderType = "ONCE";

    private Integer remindBefore = 30;
}
