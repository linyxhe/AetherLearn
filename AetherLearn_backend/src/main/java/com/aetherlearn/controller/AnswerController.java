package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.AnswerSubmitRequest;
import com.aetherlearn.dto.GradeResultVO;
import com.aetherlearn.dto.ReviewRequest;
import com.aetherlearn.dto.SubmissionSummaryVO;
import com.aetherlearn.service.AssignmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作答与批改控制器（F-HW 作业模块）
 * <p>路径：/api/answer/** 。学生作答提交/查看结果；教师查看提交情况与复核。</p>
 */
@RestController
@RequestMapping("/api/answer")
public class AnswerController {

    private final AssignmentService assignmentService;

    public AnswerController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * 学生提交作答并自动批改
     */
    @PostMapping("/submit")
    public Result<GradeResultVO> submit(@Valid @RequestBody AnswerSubmitRequest request) {
        Long studentId = SecurityUtils.getCurrentUserId();
        return Result.success("提交成功", assignmentService.submit(studentId, request));
    }

    /**
     * 查看批改结果：学生看本人；教师/管理员可加 studentId 查看指定学生
     */
    @GetMapping("/result")
    public Result<GradeResultVO> result(@RequestParam Long assignmentId,
                                        @RequestParam(required = false) Long studentId) {
        Long viewerId = SecurityUtils.getCurrentUserId();
        Integer role = SecurityUtils.getCurrentRole();
        // 仅教师/管理员可指定学生；学生只能看自己
        Long target = (role != null && role != RoleConstant.STUDENT && studentId != null)
                ? studentId : viewerId;
        return Result.success(assignmentService.getResult(target, assignmentId));
    }

    /**
     * 教师查看某作业提交情况（按学生汇总）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/submissions")
    public Result<List<SubmissionSummaryVO>> submissions(@RequestParam Long assignmentId) {
        return Result.success(assignmentService.getSubmissions(assignmentId));
    }

    /**
     * 教师复核单题作答（调分/反馈/复核状态）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping("/review")
    public Result<Void> review(@Valid @RequestBody ReviewRequest request) {
        assignmentService.review(request);
        return Result.success();
    }
}
