package com.aetherlearn.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问答历史记录 VO（M4 问答历史来源显示）
 * <p>在 QaRecord 基础上增加 sources 列表，用于历史记录展示。</p>
 */
@Data
public class QaHistoryVO {

    /** 记录ID */
    private Long id;

    /** 用户提问 */
    private String question;

    /** 系统回答 */
    private String answer;

    /** 来源切片列表 */
    private List<QaSource> sources;

    /** 是否使用大模型：1-是 0-否 */
    private Integer useLlm;

    /** 回答耗时（毫秒） */
    private Integer costMs;

    /** 提问时间 */
    private LocalDateTime createTime;
}
