package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程实体（F-COURSE 课程管理模块）
 * <p>对应表 {@code course}；v2.0 新增 {@code isDeleted} 软删除标志，
 * 删除课程时置 1，列表查询默认过滤 {@code is_deleted=0}。</p>
 */
@Data
@TableName("course")
public class Course implements Serializable {

    /** 课程ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程名称 */
    private String courseName;

    /** 课程编号 */
    private String courseCode;

    /** 授课教师ID（关联 sys_user.id） */
    private Long teacherId;

    /** 课程简介 */
    private String description;

    /** 封面图路径（uploads/course） */
    private String cover;

    /** 加入邀请码（6 位随机串，用于学生扫码/输码加入） */
    private String inviteCode;

    /** 状态：1-开课 0-下架 */
    private Integer status;

    /** 软删除标志：0-未删 1-已删（v2.0 新增） */
    @TableLogic
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;
}
