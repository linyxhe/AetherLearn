package com.aetherlearn.service;

import com.aetherlearn.entity.KnowledgeDoc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课程知识库服务（F-KB 知识库模块）
 * <p>负责知识文档上传（解析 + 切片入库）、按课程列举、软删除。</p>
 */
public interface KnowledgeService {

    /**
     * 上传并解析文档，切片入库
     *
     * @param courseId 课程ID
     * @param uploadBy 上传人ID
     * @param file     上传的文件（pdf/docx/md/txt）
     * @return 文档记录（含切片数）
     */
    KnowledgeDoc upload(Long courseId, Long uploadBy, MultipartFile file);

    /**
     * 列举课程下的知识文档（按上传时间倒序）
     */
    List<KnowledgeDoc> listByCourse(Long courseId);

    /**
     * 软删除文档（级联隐藏其切片）
     */
    void delete(Long docId);
}
