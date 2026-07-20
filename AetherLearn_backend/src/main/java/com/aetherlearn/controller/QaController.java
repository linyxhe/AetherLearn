package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.QaAnswer;
import com.aetherlearn.dto.QaAskRequest;
import com.aetherlearn.entity.QaRecord;
import com.aetherlearn.service.QaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能答疑控制器（F-QA 智能答疑模块）
 * <p>路径：/api/qa/** 。</p>
 */
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    /**
     * 提问并获取回答（检索 + 大模型 / 降级）
     */
    @PostMapping("/ask")
    public Result<QaAnswer> ask(@Valid @RequestBody QaAskRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(qaService.ask(userId, request));
    }

    /**
     * 当前用户的问答历史
     */
    @GetMapping("/history")
    public Result<List<QaRecord>> history(@RequestParam(required = false) Long courseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(qaService.history(userId, courseId));
    }
}
