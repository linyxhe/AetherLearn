package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.AiTeachingAdviceVO;
import com.aetherlearn.service.AiTeachingAdviceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 教学建议控制器（F-AI-ADVICE）
 */
@RestController
@RequestMapping("/api/ai-advice")
public class AiAdviceController {

    private final AiTeachingAdviceService aiTeachingAdviceService;

    public AiAdviceController(AiTeachingAdviceService aiTeachingAdviceService) {
        this.aiTeachingAdviceService = aiTeachingAdviceService;
    }

    /** 生成教师教学建议 */
    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/teacher")
    public Result<AiTeachingAdviceVO> teacherAdvice() {
        return Result.success(aiTeachingAdviceService.generateTeachingAdvice(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRole()));
    }
}

