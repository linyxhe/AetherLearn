package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.dto.QaSource;
import com.aetherlearn.entity.KnowledgeChunk;
import com.aetherlearn.entity.KnowledgeDoc;
import com.aetherlearn.kb.DocumentParser;
import com.aetherlearn.ai.PythonAiClient;
import com.aetherlearn.kb.TextChunker;
import com.aetherlearn.hybrid.HybridRetriever;
import com.aetherlearn.mapper.KnowledgeChunkMapper;
import com.aetherlearn.mapper.KnowledgeDocMapper;
import com.aetherlearn.mapper.CourseMapper;
import com.aetherlearn.mapper.CourseStudentMapper;
import com.aetherlearn.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 课程知识库服务实现（F-KB 知识库模块）
 * <p>上传流程：保存原文件 → PDF/Word/文本解析 → 文本切片 → 向量化入库；检索时优先混合检索，失败时回退 BM25。</p>
 */
@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final PythonAiClient pythonAiClient;
    private final HybridRetriever hybridRetriever;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;

    /** 上传根目录（来自 application.yml 的 file.upload-dir） */
    @Value("${file.upload-dir}")
    private String uploadDir;
    /** 切片字符上限（来自 application.yml 的 ai.chunk-size） */
    @Value("${ai.chunk-size:600}")
    private int chunkSize;
    /** 事务提交后执行向量化，避免阻塞上传接口。 */
    private final TaskExecutor taskExecutor;

    public KnowledgeServiceImpl(KnowledgeDocMapper docMapper, KnowledgeChunkMapper chunkMapper,
                                DocumentParser documentParser, TextChunker textChunker,
                                PythonAiClient pythonAiClient, HybridRetriever hybridRetriever,
                                CourseMapper courseMapper, CourseStudentMapper courseStudentMapper,
                                @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.documentParser = documentParser;
        this.textChunker = textChunker;
        this.pythonAiClient = pythonAiClient;
        this.hybridRetriever = hybridRetriever;
        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;
        this.taskExecutor = taskExecutor;
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
            List<Long> chunkIds = new ArrayList<>();
            for (String c : chunks) {
                KnowledgeChunk kc = new KnowledgeChunk();
                kc.setDocId(doc.getId());
                kc.setCourseId(courseId);
                kc.setSeq(seq++);
                kc.setContent(c);
                kc.setCharLen(c.length());
                kc.setCreateTime(LocalDateTime.now());
                chunkMapper.insert(kc);
                chunkIds.add(kc.getId());
            }
            doc.setChunkCount(chunks.size());
            doc.setStatus(1); // 1-已解析
            docMapper.updateById(doc);

            // 6) 事务提交后异步向量化：AI 服务不可用时保留 MySQL 全文检索兜底
            queueChunksToVectorStore(courseId, doc.getId(), chunkIds, chunks);
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

    /** 异步向量化最大重试次数。 */
    private static final int MAX_VECTOR_RETRY = 3;
    /** 首次重试等待时间，后续按指数退避递增。 */
    private static final long VECTOR_RETRY_BASE_DELAY_MS = 500L;

    /**
     * 在数据库事务提交后异步向量化，避免阻塞上传接口。
     * <p>事务回滚时不会触发向量化；AI 服务异常仅记录日志，MySQL 切片继续保留。
     * 短暂抖动时最多重试 {@link #MAX_VECTOR_RETRY} 次，重试耗尽不阻塞上传结果。</p>
     */
    private void queueChunksToVectorStore(Long courseId, Long docId, List<Long> chunkIds, List<String> chunks) {
        if (!pythonAiClient.isEnabled() || chunks == null || chunks.isEmpty()) {
            if (!pythonAiClient.isEnabled()) {
                log.warn("[KB] Python AI 服务未启用，跳过向量入库，保留 MySQL 检索兜底");
            }
            return;
        }

        Runnable vectorizationTask = () -> runWithRetry(
                "向量入库",
                () -> indexChunksToVectorStore(courseId, docId, chunkIds, chunks),
                courseId,
                docId
        );

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskExecutor.execute(vectorizationTask);
                }
            });
        } else {
            taskExecutor.execute(vectorizationTask);
        }
    }

    /**
     * 将切片批量写入 Python AI 服务的 Milvus 向量库。
     * <p>写入失败时抛出异常，由 {@link #runWithRetry} 统一重试；重试耗尽只记录日志，保留 MySQL 全文检索兜底。</p>
     */
    private void indexChunksToVectorStore(Long courseId, Long docId, List<Long> chunkIds, List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<List<Float>> embeddings = pythonAiClient.embedDocuments(chunks);
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalStateException("向量化结果数量与切片数量不一致");
        }

        List<Map<String, Object>> payload = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("chunk_id", chunkIds.get(i));
            item.put("course_id", courseId);
            item.put("doc_id", docId);
            item.put("seq", i);
            item.put("content", chunks.get(i));
            item.put("embedding", embeddings.get(i));
            payload.add(item);
        }

        PythonAiClient.IndexResult result = pythonAiClient.indexChunksDetailed(payload);
        if (!result.isSuccess()) {
            throw new IllegalStateException("向量入库未完整成功：status=" + result.getStatus()
                    + ", inserted=" + result.getInserted() + ", error=" + result.getError());
        }
        log.info("[KB] 向量入库成功：courseId={}, docId={}, inserted={}", courseId, docId, result.getInserted());
    }

    /**
     * 文档软删除后异步清理 Milvus 向量，避免检索继续命中已删除文档。
     * <p>删除操作同样在事务提交后执行，AI 服务异常不会回滚 MySQL 软删除。</p>
     */
    private void queueChunksDeletionToVectorStore(Long courseId, Long docId) {
        if (!pythonAiClient.isEnabled()) {
            log.warn("[KB] Python AI 服务未启用，跳过 Milvus 向量删除，保留 MySQL 软删除兜底");
            return;
        }

        Runnable deletionTask = () -> {
            int deleted = deleteChunksWithRetry(courseId, docId);
            if (deleted == 0) {
                log.warn("[KB] Milvus 中未找到待删除向量：courseId={}, docId={}", courseId, docId);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskExecutor.execute(deletionTask);
                }
            });
        } else {
            taskExecutor.execute(deletionTask);
        }
    }

    /**
     * 执行带指数退避的有界重试。
     * <p>重试耗尽后只记录错误日志，不向上抛出，避免影响上传或删除主流程。</p>
     */
    private void runWithRetry(String action, Runnable task, Long courseId, Long docId) {
        for (int attempt = 1; attempt <= MAX_VECTOR_RETRY; attempt++) {
            try {
                task.run();
                return;
            } catch (Exception e) {
                if (attempt >= MAX_VECTOR_RETRY) {
                    log.error("[KB] {}重试 {} 次后仍失败，已保留 MySQL 数据：courseId={}, docId={}",
                            action, MAX_VECTOR_RETRY, courseId, docId, e);
                    return;
                }
                long delay = VECTOR_RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                log.warn("[KB] {}第 {} 次失败，{} ms 后重试：courseId={}, docId={}, error={}",
                        action, attempt, delay, courseId, docId, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[KB] {}重试被中断，停止后续重试：courseId={}, docId={}", action, courseId, docId);
                    return;
                }
            }
        }
    }

    /** 删除 Milvus 向量并做有界重试；重试耗尽返回 0，由调用方记录日志。 */
    private int deleteChunksWithRetry(Long courseId, Long docId) {
        for (int attempt = 1; attempt <= MAX_VECTOR_RETRY; attempt++) {
            try {
                return pythonAiClient.deleteChunks(courseId, docId);
            } catch (Exception e) {
                if (attempt >= MAX_VECTOR_RETRY) {
                    log.error("[KB] Milvus 向量删除重试 {} 次后仍失败，已保留 MySQL 软删除：courseId={}, docId={}",
                            MAX_VECTOR_RETRY, courseId, docId, e);
                    return 0;
                }
                long delay = VECTOR_RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                log.warn("[KB] Milvus 向量删除第 {} 次失败，{} ms 后重试：courseId={}, docId={}, error={}",
                        attempt, delay, courseId, docId, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[KB] Milvus 向量删除重试被中断：courseId={}, docId={}", courseId, docId);
                    return 0;
                }
            }
        }
        return 0;
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
        queueChunksDeletionToVectorStore(doc.getCourseId(), docId);
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
        // 调用混合检索器：优先 Python AI 服务，不可用时回退 BM25
        assertCourseAccess(courseId, userId, role, false);
        List<KnowledgeChunk> chunks = hybridRetriever.retrieve(courseId, query.trim(), topK);
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
