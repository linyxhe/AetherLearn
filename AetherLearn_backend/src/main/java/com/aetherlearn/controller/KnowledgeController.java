package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.QaSource;
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
        return Result.success("上传成功", knowledgeService.upload(courseId, uploadBy,
                SecurityUtils.getCurrentRole(), file));
    }

    /**
     * 列举课程下的知识文档
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    @GetMapping("/list")
    public Result<List<KnowledgeDoc>> list(@RequestParam Long courseId) {
        return Result.success(knowledgeService.listByCourse(courseId,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRole()));
    }

    /**
     * 删除文档（软删除，仅 教师/管理员）
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id, SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRole());
        return Result.success();
    }

    /**
     * 检索知识库切片（M2 知识库检索预览）
     * <p>使用 BM25 检索相关切片，返回来源信息和内容预览。</p>
     *
     * @param courseId 课程ID
     * @param query    搜索关键词
     * @param topK     返回数量上限（默认 10）
     * @return 按相关性降序排列的切片来源列表
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    @GetMapping("/search")
    public Result<List<QaSource>> search(@RequestParam Long courseId,
                                         @RequestParam String query,
                                         @RequestParam(defaultValue = "10") int topK) {
        return Result.success(knowledgeService.searchChunks(courseId, query, topK,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentRole()));
    }
}

