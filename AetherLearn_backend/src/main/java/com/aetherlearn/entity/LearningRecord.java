package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习行为记录实体（F-LEARN 学情模块）
 * <p>对应表 {@code learning_record}；作为学情统计的数据源。
 * 作业提交/复核后写入 {@code action_type='作业'} 的记录，供看板与学情分析（第四波）读取。</p>
 */
@Data
@TableName("learning_record")
public class LearningRecord implements Serializable {

    /** 记录ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    private Long studentId;

    /** 课程ID */
    private Long courseId;

    /** 行为类型：登录/问答/作业/浏览 */
    private String actionType;

    /** 关联对象ID（作业ID/问答ID等） */
    private Long targetId;

    /** 时长（秒） */
    private Integer duration;

    /** 关联成绩（作业得分等） */
    private Integer score;

    /** 发生时间 */
    private LocalDateTime createTime;
}
