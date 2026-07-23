package com.aetherlearn.service;

import com.aetherlearn.dto.QaAnswer;
import com.aetherlearn.dto.QaAskRequest;
import com.aetherlearn.dto.QaHistoryVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能答疑服务（F-QA 智能答疑模块）
 * <p>检索课程知识切片 → 拼接上下文 → 调用大模型生成答案；无 Key / 失败时降级为”仅知识库检索”。</p>
 */
public interface QaService {

    /**
     * 一次问答（同步）
     */
    QaAnswer ask(Long userId, QaAskRequest request);

    /**
     * 流式问答（SSE），前端通过 EventSource 消费
     */
    SseEmitter askStream(Long userId, QaAskRequest request);

    /**
     * 查询当前用户的问答历史（M4 含来源详情）
     */
    List<QaHistoryVO> history(Long userId, Long courseId);
}
