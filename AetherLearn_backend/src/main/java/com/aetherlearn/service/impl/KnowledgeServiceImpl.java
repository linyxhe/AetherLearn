package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.QaSource;
import com.aetherlearn.entity.KnowledgeChunk;
import com.aetherlearn.entity.KnowledgeDoc;
import com.aetherlearn.kb.Bm25Retriever;
import com.aetherlearn.kb.DocumentParser;
import com.aetherlearn.kb.TextChunker;
import com.aetherlearn.mapper.KnowledgeChunkMapper;
import com.aetherlearn.mapper.KnowledgeDocMapper;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.CourseStudentMapper;
import com.aetherlearn.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 课程知识库服务实现（F-KB 知识库模块）
 * <p>上传流程：保存原文件 → PDF/Word/文本解析 → 文本切片 → 落库 knowledge_doc / knowledge_chunk。</p>
 */
@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final Bm25Retriever retriever;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;

    /** 上传根目录（来自 application.yml 的 file.upload-dir） */
    @Value("${file.upload-dir}")
    private String uploadDir;
    /** 切片字符上限（来自 application.yml 的 ai.chunk-size） */
    @Value("${ai.chunk-size:600}")
    private int chunkSize;

    public KnowledgeServiceImpl(KnowledgeDocMapper docMapper, KnowledgeChunkMapper chunkMapper,
                                DocumentParser documentParser, TextChunker textChunker,
                                Bm25Retriever retriever, CourseMapper courseMapper,
                                CourseStudentMapper courseStudentMapper) {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.documentParser = documentParser;
        this.textChunker = textChunker;
        this.retriever = retriever;
        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDoc upload(Long courseId, Long uploadBy, Integer role, MultipartFile file) {
        if (courseId == null) {
            throw new BusinessException(400, "课程ID不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        assertCourseAccess(courseId, uploadBy, role, true);
        String original = file.getOriginalFilename();
        String fileType = resolveType(original);

        // 1) 保存原始文件到 uploads/knowledge/
        Path dir = Paths.get(uploadDir, "knowledge");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException(500, "创建上传目录失败：" + e.getMessage());
        }
        String ext = (original != null && original.contains(".")) ? original.substring(original.lastIndexOf(".")) : "";
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = dir.resolve(storedName);
        String accessPath = "/" + Paths.get("uploads", "knowledge", storedName).toString().replace("\\", "/");

        // 2) 先落一条“解析中”文档记录，便于前端展示状态
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setCourseId(courseId);
        doc.setTitle(original != null ? original : storedName);
        doc.setFilePath(accessPath);
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(0); // 0-解析中
        doc.setUploadBy(uploadBy);
        doc.setCreateTime(LocalDateTime.now());
        docMapper.insert(doc);

        try {
            // 3) 先解析为纯文本（必须在 transferTo 之前，避免移动临时文件后无法再读流）
            String text = documentParser.parse(file, fileType);
            if (text == null || text.isBlank()) {
                throw new BusinessException(400, "文件未提取到可检索文本，请上传可复制文字的 PDF、DOCX、Markdown 或 TXT 文件");
            }
            // 4) 保存原始文件到磁盘
            file.transferTo(target);
            // 5) 切片（重叠约 20%）
            List<String> chunks = textChunker.split(text, chunkSize, (int) (chunkSize * 0.2));
            if (chunks.isEmpty()) {
                throw new BusinessException(400, "文件未生成可检索切片，请检查文件内容后重新上传");
            }
            int seq = 0;
            for (String c : chunks) {
                KnowledgeChunk kc = new KnowledgeChunk();
                kc.setDocId(doc.getId());
                kc.setCourseId(courseId);
                kc.setSeq(seq++);
                kc.setContent(c);
                kc.setCharLen(c.length());
                kc.setCreateTime(LocalDateTime.now());
                chunkMapper.insert(kc);
            }
            doc.setChunkCount(chunks.size());
            doc.setStatus(1); // 1-已解析
            docMapper.updateById(doc);
            log.info("[KB] 文档解析完成：docId={}, 切片数={}", doc.getId(), chunks.size());
            return doc;
        } catch (BusinessException e) {
            // 业务校验错误直接返回给前端，避免被包装成笼统的“解析失败”。
            deletePhysicalFile(target);
            throw e;
        } catch (Exception e) {
            deletePhysicalFile(target);
            // 解析失败：标记文档状态为失败，不阻断事务（doc 记录保留以便排查）
            doc.setStatus(2); // 2-失败
            docMapper.updateById(doc);
            log.error("[KB] 文档解析失败：docId={}", doc.getId(), e);
            throw new BusinessException(500, "文档解析失败：" + e.getMessage());
        }
    }

    @Override
    public List<KnowledgeDoc> listByCourse(Long courseId, Long userId, Integer role) {
        assertCourseAccess(courseId, userId, role, false);
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDoc::getCourseId, courseId);
        wrapper.orderByDesc(KnowledgeDoc::getCreateTime);
        // MyBatis-Plus 全局逻辑删除会自动附加 is_deleted = 0
        return docMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long docId, Long userId, Integer role) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(404, "知识文档不存在");
        }
        // removeById 配合 @TableLogic 软删除；检索时 knowledge_chunk 已 JOIN doc.is_deleted 隔离
        assertCourseAccess(doc.getCourseId(), userId, role, true);
        docMapper.deleteById(docId);
        deletePhysicalFile(doc.getFilePath());
    }

    /**
     * 检索课程知识库切片（M2 知识库检索预览）
     * <p>调用 Bm25Retriever 获取相关切片，再关联文档标题组装 QaSource。</p>
     */
    @Override
    public List<QaSource> searchChunks(Long courseId, String query, int topK, Long userId, Integer role) {
        if (courseId == null) {
            throw new BusinessException(400, "课程ID不能为空");
        }
        if (query == null || query.isBlank()) {
            throw new BusinessException(400, "搜索关键词不能为空");
        }
        // 调用已有 BM25 检索器
        assertCourseAccess(courseId, userId, role, false);
        List<KnowledgeChunk> chunks = retriever.retrieve(courseId, query.trim(), topK);
        // 组装来源信息
        List<QaSource> sources = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            KnowledgeDoc doc = docMapper.selectById(c.getDocId());
            QaSource s = new QaSource();
            s.setDocId(c.getDocId());
            s.setDocTitle(doc != null ? doc.getTitle() : "未知文档");
            s.setContent(c.getContent());
            sources.add(s);
        }
        return sources;
    }

    /** 从文件名解析并校验文件类型 */
    /** 校验课程访问权限，教师只能维护本人课程，学生只能访问已选课程。 */
    private void assertCourseAccess(Long courseId, Long userId, Integer role, boolean write) {
        var course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        if (role != null && role == RoleConstant.ADMIN) {
            return;
        }
        if (role != null && role == RoleConstant.TEACHER
                && java.util.Objects.equals(course.getTeacherId(), userId)) {
            return;
        }
        if (!write && role != null && role == RoleConstant.STUDENT
                && userId != null && courseStudentMapper.countByCourseAndStudent(courseId, userId) > 0) {
            return;
        }
        throw new BusinessException(403, "无权访问该课程知识库");
    }

    /** 解析或切片失败时清理已落盘文件，避免产生孤儿文件。 */
    private void deletePhysicalFile(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupError) {
            log.warn("[KB] 清理失败文件异常: {}", target, cleanupError);
        }
    }

    /** 删除文档时按知识库路径安全清理物理文件。 */
    private void deletePhysicalFile(String filePath) {
        if (filePath == null || !filePath.startsWith("/uploads/knowledge/")) {
            return;
        }
        Path root = Paths.get(uploadDir, "knowledge").toAbsolutePath().normalize();
        Path target = root.resolve(filePath.substring("/uploads/knowledge/".length())).normalize();
        if (target.startsWith(root)) {
            deletePhysicalFile(target);
        }
    }

    private String resolveType(String original) {
        if (original == null || !original.contains(".")) {
            throw new BusinessException(400, "无法识别文件类型，请上传 pdf/docx/md/txt");
        }
        String ext = original.substring(original.lastIndexOf(".") + 1).toLowerCase();
        return switch (ext) {
            case "pdf", "docx", "md", "txt" -> ext;
            default -> throw new BusinessException(400, "不支持的文件类型：" + ext + "（仅支持 pdf/docx/md/txt）");
        };
    }
}
