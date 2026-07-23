package com.aetherlearn.service.impl;

import com.aetherlearn.ai.AiConfig;
import com.aetherlearn.ai.LlmClient;
import com.aetherlearn.dto.QaAnswer;
import com.aetherlearn.dto.QaAskRequest;
import com.aetherlearn.dto.QaHistoryVO;
import com.aetherlearn.dto.QaSource;
import com.aetherlearn.entity.KnowledgeChunk;
import com.aetherlearn.entity.KnowledgeDoc;
import com.aetherlearn.entity.QaRecord;
import com.aetherlearn.kb.Bm25Retriever;
import com.aetherlearn.mapper.KnowledgeChunkMapper;
import com.aetherlearn.mapper.KnowledgeDocMapper;
import com.aetherlearn.mapper.QaRecordMapper;
import com.aetherlearn.service.QaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 智能答疑服务实现（F-QA 智能答疑模块）
 * <p>核心流程：BM25 检索 → 上下文滑动窗口截断 → 大模型生成（失败降级）→ 落库问答记录。</p>
 */
@Slf4j
@Service
public class QaServiceImpl implements QaService {

    private static final ExecutorService SSE_POOL = Executors.newCachedThreadPool();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocMapper docMapper;
    private final QaRecordMapper qaRecordMapper;
    private final Bm25Retriever retriever;
    private final AiConfig aiConfig;
    private final LlmClient llmClient;

    /** 每次取 Top-K 切片（来自 application.yml 的 ai.top-k） */
    @Value("${ai.top-k:5}")
    private int topK;
    /** 上下文 token 阈值（来自 application.yml 的 ai.token-threshold） */
    @Value("${ai.token-threshold:2500}")
    private int tokenThreshold;
    /** 滑动窗口截断比例（来自 application.yml 的 ai.token-ratio） */
    @Value("${ai.token-ratio:0.8}")
    private double tokenRatio;

    public QaServiceImpl(KnowledgeChunkMapper chunkMapper, KnowledgeDocMapper docMapper,
                         QaRecordMapper qaRecordMapper, Bm25Retriever retriever,
                         AiConfig aiConfig, LlmClient llmClient) {
        this.chunkMapper = chunkMapper;
        this.docMapper = docMapper;
        this.qaRecordMapper = qaRecordMapper;
        this.retriever = retriever;
        this.aiConfig = aiConfig;
        this.llmClient = llmClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaAnswer ask(Long userId, QaAskRequest request) {
        long start = System.currentTimeMillis();
        Long courseId = request.getCourseId();
        String question = request.getQuestion().trim();

        // 1) BM25 检索相关切片（L8 记录检索耗时）
        long t1 = System.currentTimeMillis();
        List<KnowledgeChunk> chunks = retriever.retrieve(courseId, question, topK);
        long retrievalMs = System.currentTimeMillis() - t1;

        // 2) 拼接上下文（滑动窗口截断至阈值的 tokenRatio ≈ 2000 token）
        int budget = (int) (tokenThreshold * tokenRatio);
        StringBuilder ctx = new StringBuilder();
        List<QaSource> sources = new ArrayList<>();
        StringBuilder sourceIds = new StringBuilder();
        for (KnowledgeChunk c : chunks) {
            // 拼满预算即停（至少保留第一条）
            if (ctx.length() > 0 && ctx.length() + c.getContent().length() + 2 > budget) {
                break;
            }
            if (ctx.length() > 0) ctx.append("\n\n");
            ctx.append(c.getContent());
            sourceIds.append(c.getId()).append(",");
            KnowledgeDoc d = docMapper.selectById(c.getDocId());
            QaSource s = new QaSource();
            s.setDocId(c.getDocId());
            s.setDocTitle(d != null ? d.getTitle() : "未知文档");
            s.setContent(c.getContent());
            sources.add(s);
        }
        if (sourceIds.length() > 0) {
            sourceIds.setLength(sourceIds.length() - 1);
        }

        // 2.5) 获取最近对话历史（多轮追问，M3）
        String historyContext = buildHistoryContext(userId, courseId);

        // 3) 生成回答（L8 记录 LLM 耗时）
        String answer;
        boolean useLlm;
        long llmMs = 0;
        if (aiConfig.isAvailable()) {
            // 有大模型 → 无论是否检索到切片都调用 LLM
            String system;
            String user;
            if (chunks.isEmpty()) {
                system = "你是 AetherLearn 智能助教，请友好、简洁地回答学生的问题。";
                user = historyContext + "【学生问题】\n" + question;
            } else {
                system = "你是 AetherLearn 智能助教。请先直接回答学生的问题，然后如果下方知识库内容与问题相关，可以引用补充说明。"
                        + "重要规则：你必须用你自己的知识回答问题，绝对不能说'资料中未提及'、'不在知识库中'等话。"
                        + "知识库只是额外参考，不是你的知识边界。";
                user = historyContext + "【学生问题】\n" + question + "\n\n【知识库内容（仅供参考，不是你的全部知识）】\n" + ctx;
            }
            long t2 = System.currentTimeMillis();
            String llmAns = llmClient.chat(system, user);
            llmMs = System.currentTimeMillis() - t2;
            if (llmAns != null && !llmAns.isBlank()) {
                answer = llmAns.trim();
                useLlm = true;
                log.info("[QA] LLM 调用成功，耗时 {} ms，回答长度 {}", llmMs, answer.length());
            } else {
                log.warn("[QA] LLM 返回空/失败，降级为纯检索模式，耗时 {} ms，chunks={}", llmMs, chunks.size());
                answer = chunks.isEmpty()
                        ? "未在课程知识库中找到与该问题相关的资料。建议：① 由教师上传相关课程资料；② 换一种更具体的方式提问。"
                        : buildRetrievalOnlyAnswer(sources);
                useLlm = false;
            }
        } else {
            useLlm = false;
            log.info("[QA] AI 未启用（ai.enabled=false），直接走纯检索模式");
            answer = chunks.isEmpty()
                    ? "未在课程知识库中找到与该问题相关的资料。建议：① 由教师上传相关课程资料；② 换一种更具体的方式提问。"
                    : buildRetrievalOnlyAnswer(sources);
        }

        // 4) 落库（L8 记录 DB 耗时）
        long cost = System.currentTimeMillis() - start;
        long t3 = System.currentTimeMillis();

        // 4) 落库问答记录
        QaRecord record = new QaRecord();
        record.setUserId(userId);
        record.setCourseId(courseId);
        record.setQuestion(question);
        record.setAnswer(answer);
        record.setSourceChunks(sourceIds.length() > 0 ? sourceIds.toString() : null);
        // M4 存储来源详情 JSON，用于历史记录展示
        if (!sources.isEmpty()) {
            try {
                record.setSourcesJson(jsonMapper.writeValueAsString(sources));
            } catch (Exception e) {
                log.warn("[QA] 序列化 sources 失败", e);
            }
        }
        record.setUseLlm(useLlm ? 1 : 0);
        record.setCostMs((int) cost);
        record.setCreateTime(LocalDateTime.now());
        qaRecordMapper.insert(record);
        long dbMs = System.currentTimeMillis() - t3;

        // 5) 组装响应（L8 含分阶段耗时）
        QaAnswer resp = new QaAnswer();
        resp.setAnswer(answer);
        resp.setUseLlm(useLlm);
        resp.setCostMs(cost);
        resp.setSources(sources);
        resp.setRetrievalMs(retrievalMs);
        resp.setLlmMs(llmMs);
        resp.setDbMs(dbMs);
        return resp;
    }

    @Override
    public SseEmitter askStream(Long userId, QaAskRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时

        SSE_POOL.execute(() -> {
            try {
                long start = System.currentTimeMillis();
                Long courseId = request.getCourseId();
                String question = request.getQuestion().trim();

                // 1) BM25 检索（L8 记录检索耗时）
                long t1 = System.currentTimeMillis();
                List<KnowledgeChunk> chunks = retriever.retrieve(courseId, question, topK);
                long retrievalMs = System.currentTimeMillis() - t1;

                // 2) 拼接上下文
                int budget = (int) (tokenThreshold * tokenRatio);
                StringBuilder ctx = new StringBuilder();
                List<QaSource> sources = new ArrayList<>();
                StringBuilder sourceIds = new StringBuilder();
                for (KnowledgeChunk c : chunks) {
                    if (ctx.length() > 0 && ctx.length() + c.getContent().length() + 2 > budget) break;
                    if (ctx.length() > 0) ctx.append("\n\n");
                    ctx.append(c.getContent());
                    sourceIds.append(c.getId()).append(",");
                    KnowledgeDoc d = docMapper.selectById(c.getDocId());
                    QaSource s = new QaSource();
                    s.setDocId(c.getDocId());
                    s.setDocTitle(d != null ? d.getTitle() : "未知文档");
                    s.setContent(c.getContent());
                    sources.add(s);
                }
                if (sourceIds.length() > 0) sourceIds.setLength(sourceIds.length() - 1);

                // 先发送来源信息
                emitter.send(SseEmitter.event().name("sources").data(jsonMapper.writeValueAsString(sources)));

                // 2.5) 获取最近对话历史（多轮追问，M3）
                String historyContext = buildHistoryContext(userId, courseId);

                // 3) 流式生成回答（L8 记录 LLM 耗时）
                StringBuilder answerBuf = new StringBuilder();
                boolean useLlm;
                long llmMs = 0;

                if (aiConfig.isAvailable()) {
                    String system;
                    String user;
                    if (chunks.isEmpty()) {
                        system = "你是 AetherLearn 智能助教，请友好、简洁地回答学生的问题。";
                        user = historyContext + "【学生问题】\n" + question;
                    } else {
                        system = "你是 AetherLearn 智能助教。请先直接回答学生的问题，然后如果下方知识库内容与问题相关，可以引用补充说明。"
                                + "重要规则：你必须用你自己的知识回答问题，绝对不能说'资料中未提及'、'不在知识库中'等话。"
                                + "知识库只是额外参考，不是你的知识边界。";
                        user = historyContext + "【学生问题】\n" + question + "\n\n【知识库内容（仅供参考，不是你的全部知识）】\n" + ctx;
                    }

                    long t2 = System.currentTimeMillis();
                    boolean ok = llmClient.chatStream(system, user, chunk -> {
                        try {
                            answerBuf.append(chunk);
                            emitter.send(SseEmitter.event().name("chunk").data(chunk));
                        } catch (Exception e) {
                            log.warn("[QA] SSE chunk 发送失败", e);
                        }
                    });
                    llmMs = System.currentTimeMillis() - t2;

                    if (ok && answerBuf.length() > 0) {
                        useLlm = true;
                        log.info("[QA] LLM 流式调用成功，耗时 {} ms，回答长度 {}", llmMs, answerBuf.length());
                    } else {
                        log.warn("[QA] LLM 流式返回空/失败，降级为纯检索模式，耗时 {} ms，chunks={}", llmMs, chunks.size());
                        useLlm = false;
                        String fallback = chunks.isEmpty()
                                ? "未在课程知识库中找到与该问题相关的资料。建议：① 由教师上传相关课程资料；② 换一种更具体的方式提问。"
                                : buildRetrievalOnlyAnswer(sources);
                        answerBuf.setLength(0);
                        answerBuf.append(fallback);
                        emitter.send(SseEmitter.event().name("chunk").data(fallback));
                    }
                } else {
                    useLlm = false;
                    log.info("[QA] AI 未启用（ai.enabled=false），直接走纯检索模式");
                    String fallback = chunks.isEmpty()
                            ? "未在课程知识库中找到与该问题相关的资料。建议：① 由教师上传相关课程资料；② 换一种更具体的方式提问。"
                            : buildRetrievalOnlyAnswer(sources);
                    answerBuf.setLength(0);
                    answerBuf.append(fallback);
                    emitter.send(SseEmitter.event().name("chunk").data(fallback));
                }

                // 4) 落库（L8 记录 DB 耗时）
                long t3 = System.currentTimeMillis();
                QaRecord record = new QaRecord();
                record.setUserId(userId);
                record.setCourseId(courseId);
                record.setQuestion(question);
                record.setAnswer(answerBuf.toString());
                record.setSourceChunks(sourceIds.length() > 0 ? sourceIds.toString() : null);
                // M4 存储来源详情 JSON，用于历史记录展示
                if (!sources.isEmpty()) {
                    try {
                        record.setSourcesJson(jsonMapper.writeValueAsString(sources));
                    } catch (Exception ex) {
                        log.warn("[QA] 序列化 sources 失败", ex);
                    }
                }
                record.setUseLlm(useLlm ? 1 : 0);
                record.setCostMs((int) (System.currentTimeMillis() - start));
                record.setCreateTime(LocalDateTime.now());
                qaRecordMapper.insert(record);
                long dbMs = System.currentTimeMillis() - t3;
                long cost = System.currentTimeMillis() - start;
                final long finalLlmMs = llmMs;
                final long finalRetrievalMs = retrievalMs;
                final long finalDbMs = dbMs;

                // 5) 发送完成事件（L8 含分阶段耗时）
                emitter.send(SseEmitter.event().name("done").data(
                        jsonMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                            put("useLlm", useLlm);
                            put("costMs", cost);
                            put("retrievalMs", finalRetrievalMs);
                            put("llmMs", finalLlmMs);
                            put("dbMs", finalDbMs);
                        }})));
                // 等待 SSE 数据刷新到客户端后再关闭连接，避免 done 事件丢失
                Thread.sleep(200);
                emitter.complete();

            } catch (Exception e) {
                log.error("[QA] 流式问答异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("服务异常，请稍后重试"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public List<QaHistoryVO> history(Long userId, Long courseId) {
        LambdaQueryWrapper<QaRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaRecord::getUserId, userId);
        if (courseId != null) {
            wrapper.eq(QaRecord::getCourseId, courseId);
        }
        wrapper.orderByDesc(QaRecord::getCreateTime);
        wrapper.last("LIMIT 50");
        List<QaRecord> records = qaRecordMapper.selectList(wrapper);

        // M4 转换为 QaHistoryVO，解析 sourcesJson
        List<QaHistoryVO> result = new ArrayList<>();
        for (QaRecord r : records) {
            QaHistoryVO vo = new QaHistoryVO();
            vo.setId(r.getId());
            vo.setQuestion(r.getQuestion());
            vo.setAnswer(r.getAnswer());
            vo.setUseLlm(r.getUseLlm());
            vo.setCostMs(r.getCostMs());
            vo.setCreateTime(r.getCreateTime());
            // 解析来源 JSON
            if (r.getSourcesJson() != null && !r.getSourcesJson().isBlank()) {
                try {
                    List<QaSource> sources = jsonMapper.readValue(r.getSourcesJson(),
                            jsonMapper.getTypeFactory().constructCollectionType(List.class, QaSource.class));
                    vo.setSources(sources);
                } catch (Exception e) {
                    log.warn("[QA] 解析 sourcesJson 失败: recordId={}", r.getId(), e);
                    vo.setSources(new ArrayList<>());
                }
            } else {
                vo.setSources(new ArrayList<>());
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 构建多轮追问上下文（最近 3 轮对话历史）
     * <p>从 qa_record 表查询当前用户当前课程最近 3 条问答记录，拼接为上下文字符串。</p>
     */
    private String buildHistoryContext(Long userId, Long courseId) {
        LambdaQueryWrapper<QaRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaRecord::getUserId, userId);
        wrapper.eq(QaRecord::getCourseId, courseId);
        wrapper.orderByDesc(QaRecord::getCreateTime);
        wrapper.last("LIMIT 3");
        List<QaRecord> history = qaRecordMapper.selectList(wrapper);
        if (history == null || history.isEmpty()) {
            return "";
        }
        // 反转为时间正序（最早的在前）
        List<QaRecord> reversed = new ArrayList<>(history);
        java.util.Collections.reverse(reversed);
        StringBuilder sb = new StringBuilder();
        sb.append("【对话历史】\n");
        for (QaRecord r : reversed) {
            sb.append("学生：").append(r.getQuestion()).append("\n");
            sb.append("助教：").append(r.getAnswer()).append("\n\n");
        }
        return sb.toString();
    }

    /** 降级答案：直接展示检索到的资料片段 */
    private String buildRetrievalOnlyAnswer(List<QaSource> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("（未接入大模型，以下为从课程知识库检索到的相关资料，供你参考）\n\n");
        for (int i = 0; i < sources.size(); i++) {
            sb.append("【资料 ").append(i + 1).append("】来源：").append(sources.get(i).getDocTitle()).append("\n");
            sb.append(sources.get(i).getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
