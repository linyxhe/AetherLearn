package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作业/测验实体（F-HW 作业模块）
 * <p>对应表 {@code assignment}；v2.0 新增 {@code isDeleted} 软删除标志，
 * 删除作业时置 1，列表查询默认过滤 {@code is_deleted=0}。</p>
 */
@Data
@TableName("assignment")
public class Assignment implements Serializable {

    /** 作业ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 作业/测验标题 */
    private String title;

    /** 类型：1-作业 2-测验 */
    private Integer type;

    /** 说明 */
    private String description;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 截止时间 */
    private LocalDateTime endTime;

    /** 总分 */
    private Integer totalScore;

    /** 状态：1-进行中 0-已结束 */
    private Integer status;

    /** 软删除标志：0-未删 1-已删（v2.0 新增） */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
