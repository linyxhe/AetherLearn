package com.aetherlearn.service;

import com.aetherlearn.dto.QaAnswer;
import com.aetherlearn.dto.QaAskRequest;
import com.aetherlearn.entity.QaRecord;

import java.util.List;

/**
 * 智能答疑服务（F-QA 智能答疑模块）
 * <p>检索课程知识切片 → 拼接上下文 → 调用大模型生成答案；无 Key / 失败时降级为“仅知识库检索”。</p>
 */
public interface QaService {

    /**
     * 一次问答
     *
     * @param userId  提问用户ID
     * @param request 提问请求（课程ID + 问题）
     * @return 回答（含来源与是否使用大模型）
     */
    QaAnswer ask(Long userId, QaAskRequest request);

    /**
     * 查询当前用户的问答历史
     *
     * @param userId    用户ID
     * @param courseId  课程ID（可空，空则返回全部课程）
     * @return 历史记录列表
     */
    List<QaRecord> history(Long userId, Long courseId);
}
