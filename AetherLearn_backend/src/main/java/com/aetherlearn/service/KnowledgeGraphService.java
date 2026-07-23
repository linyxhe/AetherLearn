package com.aetherlearn.service;

import com.aetherlearn.dto.KnowledgeGraphVO;

/**
 * 知识点图谱服务接口
 */
public interface KnowledgeGraphService {

    /** 构建用户可见课程的知识点图谱 */
    KnowledgeGraphVO buildGraph(Long userId, Integer role, Long courseId);
}
