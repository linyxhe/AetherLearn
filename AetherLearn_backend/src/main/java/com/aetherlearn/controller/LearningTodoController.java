package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.LearningTodoSaveRequest;
import com.aetherlearn.entity.LearningTodo;
import com.aetherlearn.service.LearningTodoService;
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
 * 学习计划/待办控制器（F-TODO）
 */
@RestController
@RequestMapping("/api/todos")
public class LearningTodoController {

    private final LearningTodoService learningTodoService;

    public LearningTodoController(LearningTodoService learningTodoService) {
        this.learningTodoService = learningTodoService;
    }

    /** 查询我的学习待办 */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping
    public Result<List<LearningTodo>> list(@RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) Long courseId) {
        return Result.success(learningTodoService.listMine(SecurityUtils.getCurrentUserId(), status, courseId));
    }

    /** 新增/编辑学习待办 */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public Result<LearningTodo> save(@Valid @RequestBody LearningTodoSaveRequest request) {
        return Result.success("保存成功", learningTodoService.save(SecurityUtils.getCurrentUserId(), request));
    }

    /** 标记完成 */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        learningTodoService.updateStatus(SecurityUtils.getCurrentUserId(), id, 1);
        return Result.success();
    }

    /** 恢复待办 */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id) {
        learningTodoService.updateStatus(SecurityUtils.getCurrentUserId(), id, 0);
        return Result.success();
    }

    /** 删除学习待办 */
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        learningTodoService.delete(SecurityUtils.getCurrentUserId(), id);
        return Result.success();
    }
}
