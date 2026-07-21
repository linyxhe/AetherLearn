package com.aetherlearn.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 作业保存请求（F-HW 作业模块）
 * <p>教师新建/编辑作业共用；{@code id} 为空表示新建，否则为编辑。
 * 起止时间以字符串接收（前端 {@code yyyy-MM-dd HH:mm:ss}），由 Service 转为 LocalDateTime。</p>
 */
@Data
public class AssignmentSaveRequest implements Serializable {

    /** 作业ID（编辑时必填，新建时为空） */
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 标题 */
    private String title;

    /** 类型：1-作业 2-测验 */
    private Integer type;

    /** 说明 */
    private String description;

    /** 开始时间（yyyy-MM-dd HH:mm:ss） */
    private String startTime;

    /** 截止时间（yyyy-MM-dd HH:mm:ss） */
    private String endTime;

    /** 总分 */
    private Integer totalScore;
}
