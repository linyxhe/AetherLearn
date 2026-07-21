package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.AssignmentDetailVO;
import com.aetherlearn.dto.AssignmentSaveRequest;
import com.aetherlearn.dto.QuestionSaveRequest;
import com.aetherlearn.entity.Assignment;
import com.aetherlearn.entity.Question;
import com.aetherlearn.service.AssignmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作业与题目控制器（F-HW 作业模块）
 * <p>路径：/api/assignment/** 与 /api/question/** 。作业/题目管理限定 教师/管理员。</p>
 */
@RestController
@RequestMapping("/api/assignment")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * 作业列表：按角色区分（教师/管理员看管辖课程，学生看已加入课程）
     */
    @GetMapping("/list")
    public Result<List<Assignment>> list(@RequestParam(required = false) Long courseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Integer role = SecurityUtils.getCurrentRole();
        return Result.success(assignmentService.listAssignments(userId, role, courseId));
    }

    /**
     * 新建/编辑作业（仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping
    public Result<Assignment> save(@Valid @RequestBody AssignmentSaveRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        Integer role = SecurityUtils.getCurrentRole();
        return Result.success("保存成功", assignmentService.saveAssignment(request, operatorId, role));
    }

    /**
     * 删除作业（软删除，仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return Result.success();
    }

    /**
     * 作业详情（含题目）：学生视角隐藏标准答案
     */
    @GetMapping("/{id}/detail")
    public Result<AssignmentDetailVO> detail(@PathVariable Long id) {
        Integer role = SecurityUtils.getCurrentRole();
        boolean hideAnswer = role != null && role == RoleConstant.STUDENT;
        return Result.success(assignmentService.getDetail(id, hideAnswer));
    }

    /**
     * 新增/编辑题目（仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping("/question")
    public Result<Question> saveQuestion(@Valid @RequestBody QuestionSaveRequest request) {
        return Result.success("保存成功", assignmentService.saveQuestion(request));
    }

    /**
     * 删除题目（仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/question/{id}")
    public Result<Void> deleteQuestion(@PathVariable Long id) {
        assignmentService.deleteQuestion(id);
        return Result.success();
    }
}
