package com.aetherlearn.service.impl;

import com.aetherlearn.ai.AiConfig;
import com.aetherlearn.ai.PythonAiClient;
import com.aetherlearn.ai.StudentContextBuilder;
import com.aetherlearn.common.BusinessException;
import com.aetherlearn.dto.QaAnswer;
import com.aetherlearn.dto.QaAskRequest;
import com.aetherlearn.dto.QaHistoryVO;
import com.aetherlearn.dto.QaSource;
import com.aetherlearn.entity.KnowledgeChunk;
import com.aetherlearn.entity.KnowledgeDoc;
import com.aetherlearn.entity.QaRecord;
import com.aetherlearn.hybrid.HybridRetriever;
import com.aetherlearn.mapper.KnowledgeChunkMapper;
import com.aetherlearn.mapper.KnowledgeDocMapper;
import com.aetherlearn.mapper.CourseStudentMapper;
import com.aetherlearn.mapper.QaRecordMapper;
import com.aetherlearn.service.QaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 智能答疑服务实现（F-QA 智能答疑模块）
 * <p>核心流程：混合检索（失败回退 BM25）→ 上下文滑动窗口截断 → 大模型生成（失败降级）→ 落库问答记录。</p>
 * <p>同步与流式问答共用检索、上下文拼装、历史读取和指标字段，避免两条链路行为不一致。</p>
 */
@Slf4j
@Service
public class QaServiceImpl implements QaService {

    private static final ExecutorService SSE_POOL = Executors.newCachedThreadPool();
    private static final String EMPTY_ANSWER = "未在课程知识库中找到与该问题相关的资料。建议：① 由教师上传相关课程资料；② 换一种更具体的方式提问。";
    /** 寒暄/元问题在无大模型时的固定回复。 */
    private static final String SMALL_TALK_REPLY = "你好呀！我是 AetherLearn 智能助教，可以问我 Java、编程和计算机基础相关的问题～";
    /** 寒暄、致谢、身份询问等无需检索知识库的短语。 */
    private static final Pattern SMALL_TALK_TOKEN = Pattern.compile(
            "你好|您好|哈喽|哈啰|hello|hi|hey|嗨|在吗|在么|早上好|中午好|下午好|晚上好|晚安"
                    + "|谢谢|多谢|感谢|再见|拜拜|你是谁|你叫什么|你会什么|你能做什么|你能干什么"
                    + "|介绍一下你自己|自我介绍|帮助|怎么用|使用说明|呀|啊|哦|哈|呢|吧|嘛|了|啦|哟|哇",
            Pattern.CASE_INSENSITIVE);
    /** 标点与空白：判断寒暄时先剔除。 */
    private static final Pattern PUNCTUATION = Pattern.compile("[\\s\\p{Punct}，。！？、；：“”‘’（）【】《》…~～]+");
    /** 回答由 Python 编排服务（LangGraph）生成。 */
    private static final String LLM_PROVIDER_PYTHON = "python";
    /** 未使用大模型，直接返回检索结果。 */
    private static final String LLM_PROVIDER_RETRIEVAL = "retrieval";
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocMapper docMapper;
    private final QaRecordMapper qaRecordMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final HybridRetriever hybridRetriever;
    private final AiConfig aiConfig;
    private final PythonAiClient pythonAiClient;
    private final StudentContextBuilder studentContextBuilder;

    /** 每次取 Top-K 切片（来自 application.yml 的 ai.top-k） */
    @Value("${ai.top-k:5}")
    private int topK;
    /** 上下文 token 阈值（来自 application.yml 的 ai.token-threshold） */
    @Value("${ai.token-threshold:2500}")
    private int tokenThreshold;
    /** 滑动窗口截断比例（来自 application.yml 的 ai.token-ratio） */
    @Value("${ai.token-ratio:0.8}")
    private double tokenRatio;
    /** 是否启用 ReAct 智能助教（工具调用）；关闭时走固定流程 */
    @Value("${ai.agent.enabled:true}")
    private boolean agentEnabled;
    /** 工具调用轮数上限 */
    @Value("${ai.agent.max-tool-rounds:3}")
    private int agentMaxToolRounds;
    /**
     * Agent 模式下首轮检索是否跳过精排（默认跳过）。
     *
     * <p>CPU 上精排 12 条候要约 10.8s，是单问最大的耗时项；而 Agent 本来就会自己判断
     * 资料够不够、必要时用工具再检索一次，首轮抢先付这 10.8s 并不划算。
     * 关闭后行为与引入 Agent 前完全一致（首轮带精排）。</p>
     */
    @Value("${ai.agent.skip-rerank-on-first-pass:true}")
    private boolean agentSkipRerankOnFirstPass;

    public QaServiceImpl(KnowledgeChunkMapper chunkMapper, KnowledgeDocMapper docMapper,
                         QaRecordMapper qaRecordMapper, HybridRetriever hybridRetriever,
                         AiConfig aiConfig, PythonAiClient pythonAiClient, CourseStudentMapper courseStudentMapper,
                         StudentContextBuilder studentContextBuilder) {
        this.chunkMapper = chunkMapper;
        this.docMapper = docMapper;
        this.qaRecordMapper = qaRecordMapper;
        this.hybridRetriever = hybridRetriever;
        this.aiConfig = aiConfig;
        this.courseStudentMapper = courseStudentMapper;
        this.pythonAiClient = pythonAiClient;
        this.studentContextBuilder = studentContextBuilder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaAnswer ask(Long userId, QaAskRequest request) {
        long start = System.currentTimeMillis();
        Long courseId = request.getCourseId();
        ensureStudentCourseAccess(userId, courseId);
        String question = request.getQuestion().trim();

        // 1) 混合检索：优先向量召回，失败时回退到 MySQL FULLTEXT + BM25（L8 记录检索耗时）
        Preparation preparation = prepareAnswer(userId, courseId, question);

        // 2) 生成回答：大模型调用统一由 Python 编排服务承担；调用失败或 AI 关闭时
        //    降级为"直接返回检索结果"，Java 侧不再直连任何大模型（L8 记录 LLM 耗时）
        String answer;
        boolean useLlm;
        long llmMs = 0;
        String llmProvider;
        String llmFallbackReason = null;
        int agentToolRounds = 0;

        if (aiConfig.isAvailable()) {
            long t2 = System.currentTimeMillis();
            PythonAiClient.QaLlmResult llm = pythonAiClient.qaChat(
                    question, preparation.history(), courseId,
                    toChunkPayload(preparation.chunks()), topK, hybridRetriever.getRerankTopK(),
                    preparation.budget(), preparation.smallTalk(), agentOptions(userId, courseId));
            llmMs = System.currentTimeMillis() - t2;
            if (llm.isSuccess()) {
                answer = llm.getAnswer().trim();
                useLlm = true;
                llmProvider = LLM_PROVIDER_PYTHON;
                // agent 路径下模型可能自己又检索了几轮，Python 会在 sources 里带回这些切片；
                // 这里回查 MySQL 补 docTitle、过软删除后与首轮来源合并（来源所有权仍在 Java）
                List<QaSource> extra = toSourcesFromChunks(llm.getSources());
                if (!extra.isEmpty()) {
                    List<QaSource> merged = new ArrayList<>(preparation.sources());
                    int added = appendSources(merged, extra);
                    preparation = preparation.withSources(merged);
                    log.info("[QA] Agent 工具检索返回 {} 条，其中新增来源 {} 条，合并后共 {} 条",
                            extra.size(), added, merged.size());
                }
                agentToolRounds = llm.getToolRounds();
                log.info("[QA] Python 编排调用成功，总耗时 {} ms（模型 {} ms），工具轮数 {}，回答长度 {}",
                        llmMs, llm.getLlmMs(), agentToolRounds, answer.length());
            } else {
                llmFallbackReason = llm.getFailureReason();
                answer = buildFallbackAnswer(preparation);
                useLlm = false;
                llmProvider = LLM_PROVIDER_RETRIEVAL;
                log.warn("[QA] Python 编排失败（{}），降级为纯检索模式，耗时 {} ms，chunks={}",
                        llmFallbackReason, llmMs, preparation.chunks().size());
            }
        } else {
            llmFallbackReason = "ai_disabled";
            answer = buildFallbackAnswer(preparation);
            useLlm = false;
            llmProvider = LLM_PROVIDER_RETRIEVAL;
            log.info("[QA] AI 未启用（ai.enabled=false），直接走纯检索模式");
        }

        // 3) 落库（L8 记录 DB 耗时）
        long cost = System.currentTimeMillis() - start;
        long t3 = System.currentTimeMillis();
        QaRecord record = buildRecord(userId, courseId, question, answer, useLlm, preparation, cost);
        qaRecordMapper.insert(record);
        long dbMs = System.currentTimeMillis() - t3;

        // 4) 组装响应（L8 含分阶段耗时和降级原因）
        QaAnswer resp = new QaAnswer();
        resp.setAnswer(answer);
        resp.setUseLlm(useLlm);
        resp.setCostMs(cost);
        resp.setSources(preparation.sources());
        applyRetrievalMetrics(resp, preparation.retrieval());
        resp.setLlmMs(llmMs);
        resp.setDbMs(dbMs);
        resp.setLlmProvider(llmProvider);
        resp.setLlmFallbackReason(llmFallbackReason);
        resp.setAgentToolRounds(agentToolRounds);
        return resp;
    }

    @Override
    public SseEmitter askStream(Long userId, QaAskRequest request) {
        ensureStudentCourseAccess(userId, request.getCourseId());
        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时

        SSE_POOL.execute(() -> {
            try {
                long start = System.currentTimeMillis();
                Long courseId = request.getCourseId();
                String question = request.getQuestion().trim();

                // 1) 混合检索并拼接上下文，与同步问答共用同一套准备逻辑
                Preparation preparation = prepareAnswer(userId, courseId, question);

                // 先发送来源信息
                emitter.send(SseEmitter.event().name("sources")
                        .data(jsonMapper.writeValueAsString(preparation.sources())));

                // 2) 流式生成回答（L8 记录 LLM 耗时）；大模型调用统一走 Python 编排服务
                StringBuilder answerBuf = new StringBuilder();
                boolean useLlm;
                long llmMs = 0;
                String llmProvider;
                String llmFallbackReason = null;
                int agentToolRounds = 0;

                if (aiConfig.isAvailable()) {
                    long t2 = System.currentTimeMillis();
                    // agent 工具检索后 Python 会再发一次 sources；这里回查 MySQL 补 docTitle
                    // 与软删除过滤后，补发一条 sources 事件，前端替换为更完整的来源列表。
                    List<QaSource> agentSources = new ArrayList<>();
                    PythonAiClient.QaLlmResult llm = pythonAiClient.qaChatStream(
                            question, preparation.history(), courseId,
                            toChunkPayload(preparation.chunks()), topK, hybridRetriever.getRerankTopK(),
                            preparation.budget(), preparation.smallTalk(), agentOptions(userId, courseId),
                            chunk -> {
                                try {
                                    answerBuf.append(chunk);
                                    emitter.send(SseEmitter.event().name("chunk").data(chunk));
                                } catch (Exception e) {
                                    log.warn("[QA] SSE chunk 发送失败", e);
                                }
                            },
                            // 中间步骤：转发给前端渲染"正在检索知识库…"，不参与答案组装
                            step -> {
                                try {
                                    emitter.send(SseEmitter.event().name("step").data(step));
                                } catch (Exception e) {
                                    log.warn("[QA] SSE step 发送失败", e);
                                }
                            },
                            sourcesPayload -> appendSources(agentSources, toSourcesFromPayload(sourcesPayload)));
                    llmMs = System.currentTimeMillis() - t2;
                    agentToolRounds = llm.getToolRounds();
                    if (!agentSources.isEmpty()) {
                        List<QaSource> merged = new ArrayList<>(preparation.sources());
                        int added = appendSources(merged, agentSources);
                        preparation = preparation.withSources(merged);
                        emitter.send(SseEmitter.event().name("sources")
                                .data(jsonMapper.writeValueAsString(merged)));
                        log.info("[QA] Agent 工具检索返回 {} 条，其中新增来源 {} 条，合并后共 {} 条",
                                agentSources.size(), added, merged.size());
                    }

                    if (llm.isSuccess()) {
                        useLlm = true;
                        llmProvider = LLM_PROVIDER_PYTHON;
                        log.info("[QA] Python 流式编排成功，总耗时 {} ms（模型 {} ms），回答长度 {}",
                                llmMs, llm.getLlmMs(), answerBuf.length());
                    } else if (answerBuf.length() > 0) {
                        // 硬规则：已经推给前端的文本绝不重生成，否则用户会看到两遍答案。
                        // 保留已到文本 + 正常发 done，只把中断原因记录下来。
                        useLlm = true;
                        llmProvider = LLM_PROVIDER_PYTHON;
                        llmFallbackReason = llm.getFailureReason() == null
                                ? "python_stream_interrupted" : llm.getFailureReason();
                        log.warn("[QA] Python 流式中断（{}），保留已生成 {} 字，不重新生成",
                                llmFallbackReason, answerBuf.length());
                    } else {
                        log.warn("[QA] Python 流式返回空/失败（{}），降级为纯检索模式，耗时 {} ms，chunks={}",
                                llm.getFailureReason(), llmMs, preparation.chunks().size());
                        useLlm = false;
                        llmProvider = LLM_PROVIDER_RETRIEVAL;
                        llmFallbackReason = llm.getFailureReason();
                        String fallback = buildFallbackAnswer(preparation);
                        answerBuf.setLength(0);
                        answerBuf.append(fallback);
                        emitter.send(SseEmitter.event().name("chunk").data(fallback));
                    }
                } else {
                    useLlm = false;
                    llmProvider = LLM_PROVIDER_RETRIEVAL;
                    llmFallbackReason = "ai_disabled";
                    log.info("[QA] AI 未启用（ai.enabled=false），直接走纯检索模式");
                    String fallback = buildFallbackAnswer(preparation);
                    answerBuf.setLength(0);
                    answerBuf.append(fallback);
                    emitter.send(SseEmitter.event().name("chunk").data(fallback));
                }

                // 3) 落库（L8 记录 DB 耗时）
                long cost = System.currentTimeMillis() - start;
                long t3 = System.currentTimeMillis();
                QaRecord record = buildRecord(userId, courseId, question, answerBuf.toString(),
                        useLlm, preparation, cost);
                qaRecordMapper.insert(record);
                long dbMs = System.currentTimeMillis() - t3;

                // 4) 发送完成事件（L8 含分阶段耗时和降级原因）
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("useLlm", useLlm);
                done.put("costMs", cost);
                done.put("retrievalMs", preparation.retrieval().getRetrievalMs());
                done.put("embeddingMs", preparation.retrieval().getEmbeddingMs());
                done.put("milvusMs", preparation.retrieval().getMilvusMs());
                done.put("rerankMs", preparation.retrieval().getRerankMs());
                done.put("llmMs", llmMs);
                done.put("dbMs", dbMs);
                done.put("retrievalStrategy", preparation.retrieval().getStrategy());
                done.put("fallbackReason", preparation.retrieval().getFallbackReason());
                done.put("embeddingStatus", preparation.retrieval().getEmbeddingStatus());
                done.put("milvusStatus", preparation.retrieval().getMilvusStatus());
                done.put("rerankStatus", preparation.retrieval().getRerankStatus());
                // 末尾追加两个字段（既有 13 个字段保持不变）：前端据此区分"模型生成"与"仅检索结果"
                done.put("llmProvider", llmProvider);
                done.put("llmFallbackReason", llmFallbackReason);
                // Agent 可观测性：前端据此展示"Agent · N 轮工具"，0 表示模型没调用工具
                done.put("agentToolRounds", agentToolRounds);
                emitter.send(SseEmitter.event().name("done").data(jsonMapper.writeValueAsString(done)));

                // 等待 SSE 数据刷新到客户端后再关闭连接，避免 done 事件丢失
                Thread.sleep(200);
                emitter.complete();

            } catch (Exception e) {
                log.error("[QA] 流式问答异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("服务异常，请稍后重试"));
                } catch (Exception ignored) {
                    // 连接可能已断开，无需重复处理。
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public List<QaHistoryVO> history(Long userId, Long courseId) {
        if (courseId != null) {
            ensureStudentCourseAccess(userId, courseId);
        }
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
     * 执行检索、上下文拼装和历史读取，供同步与流式问答共用。
     *
     * @param userId   学生ID
     * @param courseId 课程ID
     * @param question 已清洗的问题文本
     * @return 问答准备结果，包含切片、上下文、来源和检索指标
     */
    private Preparation prepareAnswer(Long userId, Long courseId, String question) {
        List<Map<String, Object>> history = buildHistoryMessages(userId, courseId);
        int budget = contextBudget();

        // 寒暄/致谢/身份询问这类问题没有知识需求：跳过检索，直接交给大模型，避免无意义的
        // 向量召回和精排耗时，也不会在回答下挂一堆无关来源。
        if (isSmallTalk(question)) {
            log.info("[QA] 识别为寒暄/元问题，跳过知识库检索：{}", question);
            HybridRetriever.RetrievalResult skipped = new HybridRetriever.RetrievalResult(
                    new ArrayList<>(), 0, 0, false, 0, 0,
                    "none", null, "skipped", "skipped", "skipped");
            return new Preparation(new ArrayList<>(), new ArrayList<>(), "", history, skipped, true, budget);
        }

        // Agent 模式下首轮跳过精排：省掉约 10.8s，精度交给模型的工具调用按需索取。
        // 非 Agent 链路（含 ai.agent.enabled=false）保持带精排的既有行为。
        boolean useReranker = !(agentEnabled && agentSkipRerankOnFirstPass);
        HybridRetriever.RetrievalResult retrieval =
                hybridRetriever.retrieveWithMetrics(courseId, question, topK, useReranker);
        List<KnowledgeChunk> chunks = retrieval.getChunks();

        // 按预算截断：Java 决定"模型能看到哪些切片"，来源展示与实际上下文保持一致
        int used = 0;
        List<KnowledgeChunk> chunksForLlm = new ArrayList<>();
        List<QaSource> sources = new ArrayList<>();
        StringBuilder sourceIds = new StringBuilder();
        for (KnowledgeChunk c : chunks) {
            // 拼满预算即停（至少保留第一条）
            if (used > 0 && used + c.getContent().length() + 2 > budget) {
                break;
            }
            used += c.getContent().length() + 2;
            chunksForLlm.add(c);
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

        return new Preparation(chunksForLlm, sources, sourceIds.toString(), history, retrieval, false, budget);
    }

    /** 上下文预算（字符数）：阈值 × 截断比例 ≈ 2000。 */
    private int contextBudget() {
        return (int) (tokenThreshold * tokenRatio);
    }

    /** 把切片转成 Python 编排接口需要的载荷。 */
    private List<Map<String, Object>> toChunkPayload(List<KnowledgeChunk> chunks) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chunk_id", c.getId());
            item.put("doc_id", c.getDocId());
            item.put("seq", c.getSeq());
            item.put("content", c.getContent());
            payload.add(item);
        }
        return payload;
    }

    /**
     * 组装 ReAct 智能助教的请求选项。
     *
     * <p>学情快照由 Java 预取（不回调），并**只在本课程语境下取数**；
     * 快照构建失败只会让 agent 少一个学情工具，不影响问答本身。</p>
     */
    private PythonAiClient.QaAgentOptions agentOptions(Long userId, Long courseId) {
        if (!agentEnabled) {
            return PythonAiClient.QaAgentOptions.disabled();
        }
        Map<String, Object> studentContext = null;
        try {
            studentContext = studentContextBuilder.build(userId, courseId);
        } catch (Exception e) {
            log.warn("[QA] 学情快照构建失败，agent 将不提供学情工具：{}", e.getMessage());
        }
        return PythonAiClient.QaAgentOptions.of(agentMaxToolRounds, studentContext);
    }

    /**
     * 把 agent 工具检索到的切片转成来源（**补 docTitle、过软删除**）。
     *
     * <p>Agent 路径下"模型实际检索了哪些切片"只有 Python 侧的工具知道，但
     * <b>来源展示的所有权仍在 Java</b>：这里按 chunk_id 回查 MySQL，文档已软删除
     * 或切片不存在的一律丢弃，与首轮检索的来源口径完全一致。</p>
     *
     * @param chunks Python 侧返回的切片载荷（含 chunk_id）
     * @return 可合并进响应的来源列表
     */
    private List<QaSource> toSourcesFromChunks(List<Map<String, Object>> chunks) {
        List<QaSource> result = new ArrayList<>();
        if (chunks == null || chunks.isEmpty()) {
            return result;
        }
        Set<Long> seen = new HashSet<>();
        for (Map<String, Object> chunk : chunks) {
            Object rawId = chunk.get("chunk_id");
            if (!(rawId instanceof Number number)) {
                continue;
            }
            long chunkId = number.longValue();
            if (chunkId <= 0 || !seen.add(chunkId)) {
                continue;
            }
            KnowledgeChunk entity = chunkMapper.selectById(chunkId);
            if (entity == null) {
                continue;
            }
            KnowledgeDoc doc = docMapper.selectById(entity.getDocId());
            if (doc == null) {
                // 文档已软删除：MyBatis-Plus 逻辑删除在这里自动过滤掉，与检索链路口径一致
                continue;
            }
            QaSource source = new QaSource();
            source.setDocId(entity.getDocId());
            source.setDocTitle(doc.getTitle());
            source.setContent(entity.getContent());
            result.add(source);
        }
        return result;
    }

    /** 解析 Python 侧 sources 事件的 JSON 数组并转成来源。 */
    private List<QaSource> toSourcesFromPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> chunks = new ArrayList<>();
        try {
            JsonNode array = jsonMapper.readTree(payload);
            if (!array.isArray()) {
                return new ArrayList<>();
            }
            for (JsonNode node : array) {
                Map<String, Object> chunk = new LinkedHashMap<>();
                chunk.put("chunk_id", node.path("chunk_id").asLong(0));
                chunks.add(chunk);
            }
        } catch (Exception e) {
            log.warn("[QA] 解析 agent 来源失败：{}", e.getMessage());
            return new ArrayList<>();
        }
        return toSourcesFromChunks(chunks);
    }

    /**
     * 追加来源（按文档 + 内容去重，避免 agent 多次检索同一篇时来源重复堆叠）。
     *
     * @return **实际新增**的条数（不是传入条数）：agent 检索到的切片可能与首轮重叠，
     *         日志里报"补充 N 条"必须是真相，否则会让人以为来源变多了
     */
    private int appendSources(List<QaSource> target, List<QaSource> extra) {
        Set<String> existing = new HashSet<>();
        for (QaSource source : target) {
            existing.add(source.getDocId() + "|" + source.getContent());
        }
        int added = 0;
        for (QaSource source : extra) {
            if (existing.add(source.getDocId() + "|" + source.getContent())) {
                target.add(source);
                added++;
            }
        }
        return added;
    }

    /**
     * 判断问题是否为寒暄/致谢/身份询问等无需检索的短句。
     * <p>做法是剔除寒暄词、语气词和标点后看是否还有实质内容，
     * 例如「你好呀」为空 → 寒暄；「你好，Java 多态是什么」剩下「Java多态是什么」→ 正常检索。</p>
     */
    static boolean isSmallTalk(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String remainder = SMALL_TALK_TOKEN.matcher(question).replaceAll("");
        remainder = PUNCTUATION.matcher(remainder).replaceAll("");
        return remainder.isEmpty();
    }

    /** 降级答案：寒暄走固定回复，无切片时给出引导，有切片时直接展示检索资料。 */
    private String buildFallbackAnswer(Preparation preparation) {
        if (preparation.smallTalk()) {
            return SMALL_TALK_REPLY;
        }
        return preparation.chunks().isEmpty()
                ? EMPTY_ANSWER
                : buildRetrievalOnlyAnswer(preparation.sources());
    }

    /** 组装问答记录实体，保持同步与流式落库字段一致。 */
    private QaRecord buildRecord(Long userId,
                                 Long courseId,
                                 String question,
                                 String answer,
                                 boolean useLlm,
                                 Preparation preparation,
                                 long costMs) {
        QaRecord record = new QaRecord();
        record.setUserId(userId);
        record.setCourseId(courseId);
        record.setQuestion(question);
        record.setAnswer(answer);
        record.setSourceChunks(preparation.sourceIds().isEmpty() ? null : preparation.sourceIds());
        // M4 存储来源详情 JSON，用于历史记录展示
        if (!preparation.sources().isEmpty()) {
            try {
                record.setSourcesJson(jsonMapper.writeValueAsString(preparation.sources()));
            } catch (Exception e) {
                log.warn("[QA] 序列化 sources 失败", e);
            }
        }
        record.setUseLlm(useLlm ? 1 : 0);
        record.setCostMs((int) costMs);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    /** 将检索阶段指标写入同步响应，供前端展示链路和降级状态。 */
    private void applyRetrievalMetrics(QaAnswer resp, HybridRetriever.RetrievalResult retrieval) {
        resp.setRetrievalMs(retrieval.getRetrievalMs());
        resp.setEmbeddingMs(retrieval.getEmbeddingMs());
        resp.setMilvusMs(retrieval.getMilvusMs());
        resp.setRerankMs(retrieval.getRerankMs());
        resp.setRetrievalStrategy(retrieval.getStrategy());
        resp.setFallbackReason(retrieval.getFallbackReason());
        resp.setEmbeddingStatus(retrieval.getEmbeddingStatus());
        resp.setMilvusStatus(retrieval.getMilvusStatus());
        resp.setRerankStatus(retrieval.getRerankStatus());
    }

    /** 校验学生仅能使用本人已加入课程的知识库。 */
    private void ensureStudentCourseAccess(Long userId, Long courseId) {
        if (userId == null || courseId == null
                || courseStudentMapper.countByCourseAndStudent(courseId, userId) <= 0) {
            throw new BusinessException(403, "请先加入该课程后再使用课程智能问答");
        }
    }

    /**
     * 读取多轮追问历史（最近 3 轮），组织成 role 明确的对话消息。
     *
     * <p>为什么不再拼成一段纯文本：那样模型只能从"学生：/助教："的格式去猜哪句是自己说的。
     * 现在按 <code>[{role, content}]</code> 传给 Python，由 Python 还原成
     * user / assistant 交替的消息序列，多轮语义更准。</p>
     *
     * <p>注意：本轮问题在落库之前读取，因此历史里不会包含当前这一问，不存在重复。</p>
     */
    private List<Map<String, Object>> buildHistoryMessages(Long userId, Long courseId) {
        LambdaQueryWrapper<QaRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaRecord::getUserId, userId);
        wrapper.eq(QaRecord::getCourseId, courseId);
        wrapper.orderByDesc(QaRecord::getCreateTime);
        wrapper.last("LIMIT 3");
        List<QaRecord> records = qaRecordMapper.selectList(wrapper);
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        // 反转为时间正序（最早的在前），消息顺序必须与真实对话顺序一致
        List<QaRecord> ordered = new ArrayList<>(records);
        java.util.Collections.reverse(ordered);

        List<Map<String, Object>> messages = new ArrayList<>();
        for (QaRecord r : ordered) {
            if (r.getQuestion() != null && !r.getQuestion().isBlank()) {
                messages.add(historyMessage("user", r.getQuestion()));
            }
            // 答案为空时跳过：空的 assistant 消息会被模型服务判为非法请求
            if (r.getAnswer() != null && !r.getAnswer().isBlank()) {
                messages.add(historyMessage("assistant", r.getAnswer()));
            }
        }
        return messages;
    }

    /** 组装一条历史消息（role 仅 user / assistant）。 */
    private Map<String, Object> historyMessage(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
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

    /** 问答准备结果：参与生成的切片、来源、多轮历史、检索指标与上下文预算。 */
    private record Preparation(List<KnowledgeChunk> chunks,
                               List<QaSource> sources,
                               String sourceIds,
                               List<Map<String, Object>> history,
                               HybridRetriever.RetrievalResult retrieval,
                               boolean smallTalk,
                               int budget) {

        /** 替换来源（agent 工具检索会补充来源，落库与响应都要用合并后的列表）。 */
        Preparation withSources(List<QaSource> merged) {
            return new Preparation(chunks, merged, sourceIds, history, retrieval, smallTalk, budget);
        }
    }
}
