package com.aetherlearn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 智能问答请求（F-QA 智能答疑模块）
 */
@Data
public class QaAskRequest {

    /** 课程ID（检索隔离维度） */
    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    /** 用户提问 */
    @NotBlank(message = "问题不能为空")
    private String question;
}
