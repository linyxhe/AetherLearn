package com.aetherlearn.service;

import com.aetherlearn.dto.AiLearningReportVO;

/**
 * AI 报告服务接口
 */
public interface AiReportService {

    /** 生成学生学习报告 */
    AiLearningReportVO generateStudentReport(Long studentId);
}
