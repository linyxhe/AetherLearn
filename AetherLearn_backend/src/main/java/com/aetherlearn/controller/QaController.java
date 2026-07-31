package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.QaAnswer;
import com.aetherlearn.dto.QaAskRequest;
import com.aetherlearn.dto.QaHistoryVO;
import com.aetherlearn.service.QaService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
     * 同步提问（检索 + 大模型 / 降级）
     */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/ask")
    public Result<QaAnswer> ask(@Valid @RequestBody QaAskRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(qaService.ask(userId, request));
    }

    /**
     * 流式提问（SSE），前端通过 EventSource 消费
     * <p>事件类型：sources（来源列表）、chunk（逐字回答）、done（完成元数据）、error（异常）。</p>
     */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody QaAskRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return qaService.askStream(userId, request);
    }

    /**
     * 当前用户的问答历史（M4 含来源详情）
     */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/history")
    public Result<List<QaHistoryVO>> history(@RequestParam(required = false) Long courseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(qaService.history(userId, courseId));
    }
}
