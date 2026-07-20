package com.aetherlearn.dto;

import lombok.Data;

/**
 * 课程保存（新建/编辑）请求（F-COURSE-01 课程创建/编辑）
 */
@Data
public class CourseSaveRequest {
    /** 课程ID（编辑时必填，新建时留空） */
    private Long id;
    /** 课程名称 */
    private String courseName;
    /** 课程编号 */
    private String courseCode;
    /** 课程简介 */
    private String description;
    /** 封面图路径（uploads/course） */
    private String cover;
    /** 状态：1-开课 0-下架 */
    private Integer status;
}
