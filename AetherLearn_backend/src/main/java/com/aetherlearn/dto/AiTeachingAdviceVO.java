package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 教学建议视图对象
 */
@Data
public class AiTeachingAdviceVO implements Serializable {

    /** 看板概览 */
    private DashboardStatVO.Overview overview;

    /** 总体判断 */
    private String summary;

    /** 优先关注的知识点 */
    private List<String> focusPoints;

    /** 预警学生 */
    private List<String> warningStudents;

    /** 教学动作建议 */
    private List<String> actions;

    /** AI 原文 */
    private String aiText;

    /** 是否由 AI 生成 */
    private Boolean aiGenerated;

    /** 生成时间 */
    private LocalDateTime reportTime;
}
