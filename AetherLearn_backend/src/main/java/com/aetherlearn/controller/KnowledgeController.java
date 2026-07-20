package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.entity.KnowledgeDoc;
import com.aetherlearn.service.KnowledgeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课程知识库控制器（F-KB 知识库模块）
 * <p>路径：/api/knowledge/** 。上传/删除限定 教师/管理员；列举按课程（任意登录用户）。</p>
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * 上传文档：解析 + 切片入库（仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping("/upload")
    public Result<KnowledgeDoc> upload(@RequestParam Long courseId,
                                       @RequestParam("file") MultipartFile file) {
        Long uploadBy = SecurityUtils.getCurrentUserId();
        return Result.success("上传成功", knowledgeService.upload(courseId, uploadBy, file));
    }

    /**
     * 列举课程下的知识文档
     */
    @GetMapping("/list")
    public Result<List<KnowledgeDoc>> list(@RequestParam Long courseId) {
        return Result.success(knowledgeService.listByCourse(courseId));
    }

    /**
     * 删除文档（软删除，仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.success();
    }
}
