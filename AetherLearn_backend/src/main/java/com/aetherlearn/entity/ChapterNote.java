package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 章节笔记实体（F-NOTE-01/02）
 * <p>学生针对课程章节记录私人学习笔记。</p>
 */
@Data
@TableName("chapter_note")
public class ChapterNote implements Serializable {

    /** 笔记ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程ID */
    private Long courseId;

    /** 章节ID */
    private Long chapterId;

    /** 学生ID */
    private Long studentId;

    /** 笔记标题 */
    private String title;

    /** 笔记内容 */
    private String content;

    /** 是否收藏：1-收藏 0-普通 */
    private Integer favorite;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
