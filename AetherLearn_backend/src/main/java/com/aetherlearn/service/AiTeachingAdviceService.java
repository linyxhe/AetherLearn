package com.aetherlearn.service;

import com.aetherlearn.dto.AiTeachingAdviceVO;

/**
 * AI 教学建议服务接口
 */
public interface AiTeachingAdviceService {

    /** 生成教师教学建议 */
    AiTeachingAdviceVO generateTeachingAdvice(Long teacherId, Integer role);
}
