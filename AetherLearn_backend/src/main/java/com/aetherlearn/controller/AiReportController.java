package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.AiLearningReportVO;
import com.aetherlearn.service.AiReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 报告控制器（F-AI-REPORT）
 */
@RestController
@RequestMapping("/api/ai-reports")
public class AiReportController {

    private final AiReportService aiReportService;

    public AiReportController(AiReportService aiReportService) {
        this.aiReportService = aiReportService;
    }

    /** 生成当前学生学习报告 */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/student")
    public Result<AiLearningReportVO> studentReport() {
        return Result.success(aiReportService.generateStudentReport(SecurityUtils.getCurrentUserId()));
    }
}
