package com.aetherlearn.service;

import com.aetherlearn.dto.AnswerSubmitRequest;
import com.aetherlearn.dto.AssignmentDetailVO;
import com.aetherlearn.dto.AssignmentSaveRequest;
import com.aetherlearn.dto.GradeResultVO;
import com.aetherlearn.dto.QuestionSaveRequest;
import com.aetherlearn.dto.ReviewRequest;
import com.aetherlearn.dto.SubmissionSummaryVO;
import com.aetherlearn.entity.Assignment;
import com.aetherlearn.entity.Question;

import java.util.List;

/**
 * 作业模块服务（F-HW 作业模块）
 * <p>提供作业/题目的增删查、学生作答自动批改、结果查看与教师复核。</p>
 */
public interface AssignmentService {

    /** 按角色列出作业：教师/管理员看管辖课程作业，学生看已加入课程作业 */
    List<Assignment> listAssignments(Long userId, Integer role, Long courseId);

    /** 教师新建/编辑作业（编辑时校验归属） */
    Assignment saveAssignment(AssignmentSaveRequest request, Long operatorId, Integer role);

    /** 教师软删除作业 */
    void deleteAssignment(Long id);

    /** 获取作业详情（含题目）；学生视角 hideAnswer=true 时隐藏标准答案 */
    AssignmentDetailVO getDetail(Long assignmentId, boolean hideAnswer);

    /** 教师新增/编辑题目 */
    Question saveQuestion(QuestionSaveRequest request);

    /** 教师删除题目 */
    void deleteQuestion(Long id);

    /** 学生提交作答并自动批改，返回批改结果 */
    GradeResultVO submit(Long studentId, AnswerSubmitRequest request);

    /** 学生查看某作业的历史批改结果 */
    GradeResultVO getResult(Long studentId, Long assignmentId);

    /** 教师查看某作业的提交情况汇总（按学生） */
    List<SubmissionSummaryVO> getSubmissions(Long assignmentId);

    /** 教师复核单题作答（调整分数/反馈/复核状态） */
    void review(ReviewRequest request);
}
