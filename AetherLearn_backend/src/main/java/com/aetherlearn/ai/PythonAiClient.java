package com.aetherlearn.ai;

import com.aetherlearn.common.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Python AI 服务客户端（FastAPI）。
 * <p>负责与 aetherlearn-ai-service 的 Embedding、混合检索、重排序和 Milvus 入库接口通信。</p>
 * <p>所有调用在异常时都转换为 Java 侧降级信号，不阻断上传和问答主流程。</p>
 */
@Slf4j
@Component
public class PythonAiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiConfig aiConfig;
    private final PythonAiStreamClient streamClient;

    /** Python AI 服务基础地址（application.yml 配置） */
    @Value("${ai.python-service.url:http://localhost:8000}")
    private String baseUrl;

    /** Python AI 服务内部鉴权密钥；为空时不携带鉴权头。 */
    @Value("${ai.python-service.api-key:}")
    private String apiKey;

    /** 是否允许使用 Python 混合检索服务 */
    @Value("${ai.python-service.enabled:true}")
    private boolean enabled;

    /** 最近一次混合检索失败原因（供 Java 侧降级原因细分，正常调用后清空）。 */
    private volatile String lastFailureReason;

    /** 最近一次 LLM 调用失败原因；无失败时为 null。 */
    private volatile String lastLlmFailureReason;

    /** 判断 Python AI 服务是否启用。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 最近一次混合检索失败原因；无失败时为 null。 */
    public String getLastFailureReason() {
        return lastFailureReason;
    }

    /** 最近一次 LLM 调用失败原因；无失败时为 null。 */
    public String getLastLlmFailureReason() {
        return lastLlmFailureReason;
    }

    public PythonAiClient(ObjectMapper objectMapper,
                          RestTemplateBuilder restTemplateBuilder,
                          AiConfig aiConfig,
                          PythonAiStreamClient streamClient,
                          @Value("${ai.python-service.timeout:10s}") Duration timeout) {
        this.objectMapper = objectMapper;
        this.aiConfig = aiConfig;
        this.streamClient = streamClient;
        // uvicorn/h11 只支持 HTTP/1.1：JDK HttpClient 默认会发 h2c 升级请求
        // （Upgrade: h2c），会被 uvicorn 判为非法请求并返回 400 Invalid HTTP request received.。
        // 这里显式锁定 HTTP/1.1，并直接设置连接与读取超时。
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> requestFactory)
                .build();
    }

    /**
     * 批量向量化（文档入库用）。
     *
     * @param texts 待向量化文本列表
     * @return List<List<Float>> 每个文本对应的 BGE-M3 向量
     */
    public List<List<Float>> embedDocuments(List<String> texts) {
        if (!enabled || texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Object> req = Map.of("texts", texts, "input_type", "document");
        try {
            EmbeddingResponse response = postJson("/v1/embeddings/batch", req, EmbeddingResponse.class);
            return response != null && response.getEmbeddings() != null
                    ? response.getEmbeddings() : new ArrayList<>();
        } catch (Exception e) {
            logWarn("向量化调用 Python AI 服务失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 查询向量化（检索用）。
     *
     * @param query 查询文本
     * @return BGE-M3 查询向量
     */
    public List<Float> embedQuery(String query) {
        if (!enabled || query == null || query.isBlank()) {
            return null;
        }
        Map<String, Object> req = Map.of("query", query);
        try {
            EmbeddingQueryResponse response = postJson("/v1/embeddings/query", req, EmbeddingQueryResponse.class);
            return response != null ? response.getEmbedding() : null;
        } catch (Exception e) {
            logWarn("查询向量化调用 Python AI 服务失败", e);
            return null;
        }
    }

    /**
     * 混合检索（稠密 + 稀疏 + RRF 融合，可选重排序）。
     *
     * @param courseId    课程ID
     * @param query       查询文本
     * @param topK        Python 侧召回数量
     * @param rerankTopK  重排序后保留数量
     * @param useReranker 是否启用重排序
     * @return Python 混合检索结果及分阶段耗时
     */
    public RetrievalResult hybridSearch(
            Long courseId,
            String query,
            int topK,
            int rerankTopK,
            boolean useReranker
    ) {
        if (!enabled || courseId == null || query == null || query.isBlank()) {
            return null;
        }
        int effectiveTopK = Math.max(1, Math.min(topK, 100));
        int effectiveRerankTopK = Math.max(1, Math.min(rerankTopK, 50));
        Map<String, Object> req = Map.of(
                "course_id", courseId,
                "query", query,
                "top_k", effectiveTopK,
                "rerank_top_k", effectiveRerankTopK,
                "use_reranker", useReranker
        );
        try {
            RetrievalResponse response = postJson("/v1/retrieval/hybrid", req, RetrievalResponse.class);
            if (response == null) {
                lastFailureReason = "python_empty_response";
                return null;
            }
            lastFailureReason = null;
            return new RetrievalResult(
                    response.getHits() != null ? response.getHits() : new ArrayList<>(),
                    response.getRetrievalTimeMs(),
                    response.getRerankTimeMs(),
                    true,
                    response.getEmbeddingTimeMs(),
                    response.getMilvusTimeMs(),
                    response.getStrategy(),
                    response.getFallbackReason(),
                    response.getEmbeddingStatus(),
                    response.getMilvusStatus(),
                    response.getRerankStatus()
            );
        } catch (Exception e) {
            lastFailureReason = describeFailure(e);
            logWarn("混合检索调用 Python AI 服务失败", e);
            return null;
        }
    }

    /** 把客户端异常翻译成可展示的降级原因，便于区分密钥错误、超时和服务未启动。 */
    private String describeFailure(Exception e) {
        // 必须用 HttpStatusCodeException：502/503/504 抛的是 HttpServerErrorException，
        // 只判断 HttpClientErrorException 会让所有服务端错误塌缩成笼统的 python_error。
        if (e instanceof HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            if (status == 401 || status == 403) {
                return "python_unauthorized";
            }
            if (status == 404) {
                return "python_not_found";
            }
            return "python_http_" + status;
        }
        if (e instanceof ResourceAccessException) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("timed out") || message.contains("timeout")) {
                return "python_timeout";
            }
            return "python_unreachable";
        }
        return "python_error";
    }

    /**
     * 将 MySQL 知识切片及稠密向量写入 Milvus。
     *
     * @param chunks 带 Milvus 元数据和向量的切片
     * @return 写入结果，包含成功数量、降级状态和错误信息
     */
    public IndexResult indexChunksDetailed(List<Map<String, Object>> chunks) {
        if (!enabled || chunks == null || chunks.isEmpty()) {
            return new IndexResult(0, 0, "skipped", "Python AI 服务未启用或切片为空");
        }
        // 新上传的文档使用新 doc_id，默认追加写入，避免先删后写导致历史向量丢失。
        Map<String, Object> req = Map.of("chunks", chunks, "delete_existing", false);
        try {
            IndexResponse response = postJson("/v1/knowledge/index", req, IndexResponse.class);
            if (response == null) {
                return new IndexResult(0, 0, "degraded", "Python AI 服务返回空响应");
            }
            return new IndexResult(
                    response.getInserted(),
                    response.getDeleted(),
                    response.getStatus(),
                    response.getError()
            );
        } catch (Exception e) {
            logWarn("知识切片 Milvus 入库失败", e);
            return new IndexResult(0, 0, "degraded", e.getMessage());
        }
    }

    /**
     * 兼容旧调用：只返回成功写入数量。
     *
     * @param chunks 带 Milvus 元数据和向量的切片
     * @return 成功写入数量
     */
    public int indexChunks(List<Map<String, Object>> chunks) {
        return indexChunksDetailed(chunks).getInserted();
    }

    /**
     * 删除指定文档在 Milvus 中的全部切片。
     *
     * @param courseId 课程ID
     * @param docId    文档ID
     * @return 删除数量
     */
    public int deleteChunks(Long courseId, Long docId) {
        if (!enabled || courseId == null || docId == null) {
            return 0;
        }
        Map<String, Object> req = Map.of("course_id", courseId, "doc_id", docId);
        try {
            IndexResponse response = postJson("/v1/knowledge/delete", req, IndexResponse.class);
            return response != null ? response.getDeleted() : 0;
        } catch (Exception e) {
            logWarn("知识切片 Milvus 删除失败", e);
            return 0;
        }
    }

    // ==================== LLM 编排（Java 不再直连大模型，统一由 Python 侧 LangGraph 承担） ====================

    /**
     * 问答编排（同步）：Java 传入已检索的切片，Python 负责拼上下文与生成。
     * <p>失败返回 success=false，由调用方降级为"仅检索结果"。</p>
     */
    public QaLlmResult qaChat(String question,
                              List<Map<String, Object>> history,
                              Long courseId,
                              List<Map<String, Object>> chunks,
                              int topK,
                              int rerankTopK,
                              int contextBudget,
                              boolean smallTalk,
                              QaAgentOptions agentOptions) {
        if (!enabled) {
            lastLlmFailureReason = "python_disabled";
            return QaLlmResult.failure(lastLlmFailureReason);
        }
        Map<String, Object> req = qaRequestBody(
                question, history, courseId, chunks, topK, rerankTopK, contextBudget, smallTalk, agentOptions);
        // 同步链路走 RestTemplate（整请求超时 60s）：把 Python 侧预算压到 50s，
        // 让 Python 自己的超时先生效，降级原因才是 llm_timeout 而不是传输层超时。
        req.put("timeout_seconds", 50);

        try {
            QaChatResponse response = postJson("/v1/chat/qa", req, QaChatResponse.class);
            if (response == null || response.getAnswer() == null || response.getAnswer().isBlank()) {
                lastLlmFailureReason = "python_llm_empty";
                return QaLlmResult.failure(lastLlmFailureReason);
            }
            lastLlmFailureReason = null;
            return QaLlmResult.success(
                    response.getAnswer().trim(),
                    response.getLlmMs(),
                    response.getRetrievalMs(),
                    response.getRetrievalStrategy(),
                    response.getSources() == null ? List.of() : response.getSources(),
                    response.getToolRounds());
        } catch (Exception e) {
            lastLlmFailureReason = describeLlmFailure(e);
            logWarn("问答编排调用 Python AI 服务失败", e);
            return QaLlmResult.failure(lastLlmFailureReason);
        }
    }

    /**
     * 问答编排（流式）：Python 侧边生成边推送，这里逐块回调。
     *
     * @param onChunk 每个增量文本片段
     * @return 流结果；completed=false 时由调用方决定"整轮回退"还是"保留已到文本"
     */
    public QaLlmResult qaChatStream(String question,
                                    List<Map<String, Object>> history,
                                    Long courseId,
                                    List<Map<String, Object>> chunks,
                                    int topK,
                                    int rerankTopK,
                                    int contextBudget,
                                    boolean smallTalk,
                                    QaAgentOptions agentOptions,
                                    java.util.function.Consumer<String> onChunk,
                                    java.util.function.Consumer<String> onStep,
                                    java.util.function.Consumer<String> onAgentSources) {
        if (!enabled) {
            lastLlmFailureReason = "python_disabled";
            return QaLlmResult.failure(lastLlmFailureReason);
        }
        Map<String, Object> req = qaRequestBody(
                question, history, courseId, chunks, topK, rerankTopK, contextBudget, smallTalk, agentOptions);
        req.put("timeout_seconds", 90);

        StringBuilder answer = new StringBuilder();
        long[] llmMs = {0};
        String[] strategy = {null};
        String[] failure = {null};
        int[] sourcesSeen = {0};
        int[] toolRounds = {0};

        PythonAiStreamClient.StreamOutcome outcome = streamClient.stream("/v1/chat/qa/stream", req, (event, data) -> {
            switch (event) {
                case "sources" -> {
                    // 首个 sources 是 Java 自己首轮检索的来源，Java 已自行发过；
                    // 之后（agent 工具检索后）的 sources 才需要回传给调用方组装。
                    sourcesSeen[0]++;
                    if (sourcesSeen[0] > 1 && onAgentSources != null) {
                        onAgentSources.accept(data);
                    }
                }
                case "chunk" -> {
                    answer.append(data);
                    onChunk.accept(data);
                }
                case "step" -> {
                    if (onStep != null) {
                        onStep.accept(data);
                    }
                }
                case "done" -> {
                    llmMs[0] = readLong(data, "llmMs");
                    toolRounds[0] = (int) readLong(data, "toolRounds");
                    // 检索策略由 Python 侧给出：固定流程是 hybrid/java/none，agent 路径是 "agent"。
                    // 前端据此展示"混合检索 / Agent"标签，因此必须读出来（此前一直漏读，标签永远是空）。
                    strategy[0] = readText(data, "retrievalStrategy");
                }
                case "error" -> failure[0] = normalizeStreamError(data);
                default -> {
                    // 忽略未知事件，保持向前兼容
                }
            }
        });

        if (outcome.completed() && answer.length() > 0) {
            lastLlmFailureReason = null;
            return QaLlmResult.success(answer.toString(), llmMs[0], 0, strategy[0], List.of(), toolRounds[0]);
        }
        lastLlmFailureReason = failure[0] != null ? failure[0] : describeStreamOutcome(outcome.errorCode());
        return QaLlmResult.failure(lastLlmFailureReason);
    }

    /**
     * 组装问答请求体（同步与流式共用，避免两条链路字段悄悄漂移）。
     * <p>多轮历史以 <code>[{role, content}]</code> 数组传递，由 Python 侧还原成
     * user / assistant 交替的消息序列——不再由 Java 拼成一段纯文本。</p>
     */
    private Map<String, Object> qaRequestBody(String question,
                                             List<Map<String, Object>> history,
                                             Long courseId,
                                             List<Map<String, Object>> chunks,
                                             int topK,
                                             int rerankTopK,
                                             int contextBudget,
                                             boolean smallTalk,
                                             QaAgentOptions agentOptions) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("question", question);
        req.put("history", history == null ? List.of() : history);
        req.put("course_id", courseId);
        req.put("chunks", chunks == null ? List.of() : chunks);
        req.put("top_k", topK);
        req.put("rerank_top_k", rerankTopK);
        req.put("context_budget", contextBudget);
        req.put("small_talk", smallTalk);
        req.put("llm", llmOverrides());
        // ReAct 智能助教开关与学情快照：关闭时 Python 走固定流程（改造前的行为）
        QaAgentOptions options = agentOptions == null ? QaAgentOptions.disabled() : agentOptions;
        req.put("agent", options.enabled());
        if (options.enabled()) {
            req.put("max_tool_rounds", options.maxToolRounds());
            if (options.studentContext() != null && !options.studentContext().isEmpty()) {
                req.put("student_context", options.studentContext());
            }
        }
        return req;
    }

    /**
     * ReAct 智能助教相关的请求选项（打包传递，避免问答方法参数继续膨胀）。
     *
     * @param enabled        是否启用工具调用循环
     * @param maxToolRounds  工具轮数上限
     * @param studentContext 学情快照（Java 预取；为空则 Python 侧不提供学情工具）
     */
    public record QaAgentOptions(boolean enabled, int maxToolRounds, Map<String, Object> studentContext) {

        /** 关闭 agent（走固定流程）。 */
        public static QaAgentOptions disabled() {
            return new QaAgentOptions(false, 0, null);
        }

        /** 启用 agent。 */
        public static QaAgentOptions of(int maxToolRounds, Map<String, Object> studentContext) {
            return new QaAgentOptions(true, maxToolRounds, studentContext);
        }
    }

    /**
     * 把流式客户端的收尾错误码翻译成与同步链路同一套降级原因。
     * <p>流式客户端报的是传输层错误码（io_error / stream_timeout …），
     * 直接拼成 python_llm_io_error 会让前端文案退化成技术黑话。</p>
     */
    private String describeStreamOutcome(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "python_llm_error";
        }
        return switch (errorCode) {
            case "io_error", "interrupted" -> "python_llm_unreachable";
            case "stream_timeout" -> "python_llm_timeout";
            // 流在 done 之前结束：已到的文本仍然有效，只是可能不完整
            case "stream_incomplete" -> "python_stream_incomplete";
            case "error" -> "python_llm_error";
            default -> "python_llm_" + errorCode;
        };
    }

    /**
     * 通用 LLM 任务（批改 / 出题 / 报告 / 建议）。
     * <p>预算语义与原来的 chatWithin 一致：Java 侧也做一次超时兜底，超时返回 failure。</p>
     *
     * @param budgetSeconds 业务预算（秒）；内部按 0.6 折算给 Python，留出回退时间
     */
    public TaskResult task(String promptKey,
                           Map<String, Object> variables,
                           String outputFormat,
                           Long budgetSeconds) {
        if (!enabled) {
            lastLlmFailureReason = "python_disabled";
            return TaskResult.failure(lastLlmFailureReason);
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("prompt_key", promptKey);
        req.put("variables", variables == null ? Map.of() : variables);
        req.put("output_format", outputFormat == null ? "text" : outputFormat);
        req.put("llm", llmOverrides());
        if (budgetSeconds != null) {
            req.put("timeout_seconds", Math.max(2.0, budgetSeconds * 0.6));
        }

        java.util.concurrent.CompletableFuture<TaskResult> future =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> doTask(req));
        try {
            return budgetSeconds == null
                    ? future.get()
                    : future.get(budgetSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            lastLlmFailureReason = "python_llm_timeout";
            log.warn("[PythonAiClient] {} 超过 {} 秒预算，返回本地降级结果", promptKey, budgetSeconds);
            return TaskResult.failure(lastLlmFailureReason);
        } catch (Exception e) {
            lastLlmFailureReason = describeLlmFailure(e);
            logWarn("LLM 任务调用 Python AI 服务失败", e);
            return TaskResult.failure(lastLlmFailureReason);
        }
    }

    private TaskResult doTask(Map<String, Object> req) {
        try {
            TaskResponse response = postJson("/v1/chat/task", req, TaskResponse.class);
            if (response == null || response.getRaw() == null || response.getRaw().isBlank()) {
                lastLlmFailureReason = "python_llm_empty";
                return TaskResult.failure(lastLlmFailureReason);
            }
            lastLlmFailureReason = null;
            if (response.getReflections() > 0) {
                // 可诊断性：模型第一次没按 JSON 契约输出，靠反思重试救回来了
                log.info("[PythonAiClient] {} 首次输出不符合契约，自动反思重试 {} 次后成功",
                        response.getModel(), response.getReflections());
            }
            return TaskResult.success(response.getRaw(), response.getLlmMs(),
                    response.getParseError(), response.getReflections());
        } catch (HttpStatusCodeException e) {
            // 400 属于调用方参数错误（未知 prompt_key / 缺变量），单独归类便于排查
            lastLlmFailureReason = e.getStatusCode().value() == 400 ? "python_llm_prompt_error" : describeLlmFailure(e);
            logWarn("LLM 任务调用 Python AI 服务失败", e);
            return TaskResult.failure(lastLlmFailureReason);
        } catch (Exception e) {
            lastLlmFailureReason = describeLlmFailure(e);
            logWarn("LLM 任务调用 Python AI 服务失败", e);
            return TaskResult.failure(lastLlmFailureReason);
        }
    }

    /**
     * 管理端连接测试：由 Python 侧发起一次真实调用并返回模型原话。
     * <p>连接测试是诊断工具，失败必须抛出真实原因，不能静默降级。</p>
     */
    public String testLlmConnection() {
        AiConfig.Settings settings = aiConfig.current();
        if (!settings.enabled()) {
            throw new BusinessException(400, "AI 服务已关闭，请先在配置中启用");
        }
        if (!settings.available()) {
            throw new BusinessException(400, "请先完整填写接口地址、API Key 与模型名称");
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("llm", llmOverrides());
        req.put("timeout_seconds", 40);
        String detail;
        try {
            TestConnectionResponse response = postJson("/v1/chat/test-connection", req, TestConnectionResponse.class);
            if (response == null || response.getResponse() == null || response.getResponse().isBlank()) {
                throw new BusinessException(502, "模型已连接，但没有返回内容，请检查模型是否支持对话接口");
            }
            return response.getResponse().trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            detail = describeLlmFailure(e);
            throw new BusinessException(502, "Python AI 服务调用失败（" + detail + "）：" + e.getMessage());
        }
    }

    /** 构造透传给 Python 的 LLM 覆盖项；空值不发，避免覆盖掉 Python 侧自身配置。 */
    private Map<String, Object> llmOverrides() {
        AiConfig.Settings settings = aiConfig.current();
        Map<String, Object> llm = new LinkedHashMap<>();
        if (settings.baseUrl() != null && !settings.baseUrl().isBlank()) {
            llm.put("base_url", settings.baseUrl());
        }
        if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
            llm.put("api_key", settings.apiKey());
        }
        if (settings.modelName() != null && !settings.modelName().isBlank()) {
            llm.put("model", settings.modelName());
        }
        llm.put("temperature", settings.temperature());
        return llm;
    }

    /** 流式错误事件 → 降级原因。 */
    private String normalizeStreamError(String data) {
        if (data == null || data.isBlank()) {
            return "python_llm_error";
        }
        String code = data.trim();
        if (code.startsWith("llm_") || code.startsWith("retrieval_")) {
            return "python_" + code;
        }
        return "python_llm_" + code;
    }

    /** 把 HTTP 异常归类成降级原因（与 Python 侧 detail 约定对应）。 */
    private String describeLlmFailure(Exception e) {
        // 同上：502/503/504 属于 HttpServerErrorException，必须一并归类，
        // 否则"模型返回空内容""模型服务不可用"都会显示成无信息量的 python_llm_error。
        if (e instanceof HttpStatusCodeException httpError) {
            int status = httpError.getStatusCode().value();
            String body = httpError.getResponseBodyAsString();
            if (body != null && body.contains("llm_config_incomplete")) {
                return "python_llm_config_incomplete";
            }
            if (body != null && body.contains("retrieval_unavailable")) {
                return "python_retrieval_unavailable";
            }
            if (body != null && body.contains("llm_timeout")) {
                return "python_llm_timeout";
            }
            return switch (status) {
                case 400 -> "python_llm_prompt_error";
                case 401, 403 -> "python_unauthorized";
                case 502 -> "python_llm_bad_response";
                case 503 -> "python_llm_unavailable";
                case 504 -> "python_llm_timeout";
                default -> "python_llm_http_" + status;
            };
        }
        if (e instanceof ResourceAccessException) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("timed out") || message.contains("timeout")) {
                return "python_llm_timeout";
            }
            return "python_llm_unreachable";
        }
        return "python_llm_error";
    }

    /** 粗略统计 JSON 数组长度（只用于日志/指标，不参与业务逻辑）。 */
    private int countJsonArray(String json) {
        try {
            return objectMapper.readTree(json).size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 从 JSON 里读一个长整型字段。 */
    private long readLong(String json, String field) {
        try {
            return objectMapper.readTree(json).path(field).asLong(0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 从 JSON 里读一个字符串字段；缺失或为空返回 null。 */
    private String readText(String json, String field) {
        try {
            String value = objectMapper.readTree(json).path(field).asText(null);
            return (value == null || value.isBlank()) ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    /** 问答编排结果。 */
    public static class QaLlmResult {
        private final boolean success;
        private final String answer;
        private final long llmMs;
        private final long retrievalMs;
        private final String retrievalStrategy;
        private final List<Map<String, Object>> sources;
        private final String failureReason;
        /** ReAct 路径实际执行的工具轮数；未走 agent 或未发生工具调用时为 0。 */
        private final int toolRounds;

        private QaLlmResult(boolean success, String answer, long llmMs, long retrievalMs,
                            String retrievalStrategy, List<Map<String, Object>> sources,
                            String failureReason, int toolRounds) {
            this.success = success;
            this.answer = answer;
            this.llmMs = llmMs;
            this.retrievalMs = retrievalMs;
            this.retrievalStrategy = retrievalStrategy;
            this.sources = sources;
            this.failureReason = failureReason;
            this.toolRounds = toolRounds;
        }

        public static QaLlmResult success(String answer, long llmMs, long retrievalMs,
                                          String strategy, List<Map<String, Object>> sources) {
            return new QaLlmResult(true, answer, llmMs, retrievalMs, strategy, sources, null, 0);
        }

        public static QaLlmResult success(String answer, long llmMs, long retrievalMs,
                                          String strategy, List<Map<String, Object>> sources, int toolRounds) {
            return new QaLlmResult(true, answer, llmMs, retrievalMs, strategy, sources, null, toolRounds);
        }

        public static QaLlmResult failure(String reason) {
            return new QaLlmResult(false, null, 0, 0, null, List.of(), reason, 0);
        }

        /** 本次是否真的发生了工具调用（前端据此展示"Agent · N 轮工具"）。 */
        public int getToolRounds() {
            return toolRounds;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getAnswer() {
            return answer;
        }

        public long getLlmMs() {
            return llmMs;
        }

        public long getRetrievalMs() {
            return retrievalMs;
        }

        public String getRetrievalStrategy() {
            return retrievalStrategy;
        }

        public List<Map<String, Object>> getSources() {
            return sources;
        }

        public String getFailureReason() {
            return failureReason;
        }
    }

    /** 通用 LLM 任务结果。 */
    public static class TaskResult {
        private final boolean success;
        private final String raw;
        private final long llmMs;
        private final String parseError;
        /** 反思重试次数：>0 表示第一次没给对、靠自动重试救回来了。 */
        private final int reflections;
        private final String failureReason;

        private TaskResult(boolean success, String raw, long llmMs, String parseError,
                           int reflections, String failureReason) {
            this.success = success;
            this.raw = raw;
            this.llmMs = llmMs;
            this.parseError = parseError;
            this.reflections = reflections;
            this.failureReason = failureReason;
        }

        public int getReflections() {
            return reflections;
        }

        public static TaskResult success(String raw, long llmMs, String parseError, int reflections) {
            return new TaskResult(true, raw, llmMs, parseError, reflections, null);
        }

        public static TaskResult failure(String reason) {
            return new TaskResult(false, null, 0, null, 0, reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getRaw() {
            return raw;
        }

        public long getLlmMs() {
            return llmMs;
        }

        public String getParseError() {
            return parseError;
        }

        public String getFailureReason() {
            return failureReason;
        }
    }

    /** 问答编排响应。 */
    @Data
    public static class QaChatResponse {
        private String answer;
        @JsonProperty("llm_ms")
        private long llmMs;
        @JsonProperty("retrieval_ms")
        private long retrievalMs;
        @JsonProperty("retrieval_strategy")
        private String retrievalStrategy;
        @JsonProperty("retrieval_status")
        private String retrievalStatus;
        @JsonProperty("fallback_reason")
        private String fallbackReason;
        private String provider;
        private List<Map<String, Object>> sources;
        /** agent 路径的编排元信息（tool_rounds / steps 等）；固定流程为空。 */
        private Map<String, Object> meta;

        /** 从 meta 中取工具轮数，缺失按 0 处理。 */
        public int getToolRounds() {
            Object value = meta == null ? null : meta.get("tool_rounds");
            return value instanceof Number number ? number.intValue() : 0;
        }
    }

    /** 通用任务响应。 */
    @Data
    public static class TaskResponse {
        private String raw;
        private Object data;
        @JsonProperty("llm_ms")
        private long llmMs;
        @JsonProperty("parse_error")
        private String parseError;
        /** 结构化输出失败后靠反思重试救回来的次数；0 表示首次即通过。 */
        private int reflections;
        private String provider;
        private String model;
    }

    /** 连接测试响应。 */
    @Data
    public static class TestConnectionResponse {
        private String response;
        private String provider;
        private String model;
    }

    /** 发起 JSON POST 请求。 */
    private <T> T postJson(String path, Map<String, Object> body, Class<T> responseType) {
        ResponseEntity<String> response = postJsonEntity(path, body);
        if (response == null || response.getBody() == null) {
            throw new BusinessException(502, "Python AI 服务返回空响应");
        }
        try {
            return objectMapper.readValue(response.getBody(), responseType);
        } catch (JsonProcessingException e) {
            throw new BusinessException(502, "Python AI 服务返回格式错误：" + e.getMessage());
        }
    }

    /** 设置统一 JSON 请求头和超时。 */
    private ResponseEntity<String> postJsonEntity(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-API-Key", apiKey);
        }
        // 自己用 ObjectMapper 序列化为 UTF-8 字节再发送：
        // 直接传 Map 时消息转换器可能按 ISO-8859-1 编码，中文问题会退化成 ? 丢失语义。
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "构造 AI 请求失败：" + e.getMessage());
        }
        HttpEntity<byte[]> entity = new HttpEntity<>(payload, headers);
        return restTemplate.postForEntity(resolveUrl(path), entity, String.class);
    }

    /** 统一处理基础地址末尾斜杠。 */
    private String resolveUrl(String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            return base + path.substring(1);
        }
        return base + path;
    }

    /** 日志降级处理，不阻断 Java 本地知识库路径。 */
    private void logWarn(String msg, Exception e) {
        log.warn("[PythonAiClient] {}：{}", msg, e.getMessage(), e);
    }

    /** 批量向量化响应。 */
    @Data
    public static class EmbeddingResponse {
        private List<List<Float>> embeddings;
        private int dim;
        private String model;
        private Map<String, Object> usage;
    }

    /** 查询向量化响应。 */
    @Data
    public static class EmbeddingQueryResponse {
        private List<Float> embedding;
        private int dim;
        private String model;
    }

    /** 混合检索命中详情。 */
    @Data
    public static class RetrievalHit {
        @JsonProperty("chunk_id")
        private Long chunkId;
        @JsonProperty("course_id")
        private Long courseId;
        @JsonProperty("doc_id")
        private Long docId;
        private Integer seq;
        private String content;
        private Double score;
        @JsonProperty("dense_score")
        private Double denseScore;
        @JsonProperty("sparse_score")
        private Double sparseScore;
    }

    /** 混合检索响应。 */
    @Data
    public static class RetrievalResponse {
        private List<RetrievalHit> hits;
        @JsonProperty("retrieval_time_ms")
        private long retrievalTimeMs;
        @JsonProperty("embedding_time_ms")
        private long embeddingTimeMs;
        @JsonProperty("milvus_time_ms")
        private long milvusTimeMs;
        @JsonProperty("rerank_time_ms")
        private long rerankTimeMs;
        @JsonProperty("total_candidates")
        private int totalCandidates;
        private String strategy;
        @JsonProperty("embedding_status")
        private String embeddingStatus;
        @JsonProperty("milvus_status")
        private String milvusStatus;
        @JsonProperty("rerank_status")
        private String rerankStatus;
        @JsonProperty("fallback_reason")
        private String fallbackReason;
    }

    /** 批量写入结果。 */
    @Data
    public static class IndexResponse {
        private int inserted;
        private int deleted;
        private String status;
        private String error;
    }

    /** Milvus 入库结果，区分成功数量、降级状态和错误原因。 */
    public static class IndexResult {
        private final int inserted;
        private final int deleted;
        private final String status;
        private final String error;

        public IndexResult(int inserted, int deleted, String status, String error) {
            this.inserted = inserted;
            this.deleted = deleted;
            this.status = status;
            this.error = error;
        }

        public int getInserted() {
            return inserted;
        }

        public int getDeleted() {
            return deleted;
        }

        public String getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        /** 是否完整成功。 */
        public boolean isSuccess() {
            return inserted > 0 && !"degraded".equalsIgnoreCase(status);
        }
    }

    /** Python 混合检索结果及分阶段耗时。 */
    public static class RetrievalResult {
        private final List<RetrievalHit> hits;
        private final long retrievalMs;
        private final long rerankMs;
        private final boolean usedPython;
        private final long embeddingMs;
        private final long milvusMs;
        private final String strategy;
        private final String fallbackReason;
        private final String embeddingStatus;
        private final String milvusStatus;
        private final String rerankStatus;

        /** 兼容旧调用：只关心命中、检索耗时和重排序耗时。 */
        public RetrievalResult(List<RetrievalHit> hits, long retrievalMs, long rerankMs, boolean usedPython) {
            this(hits, retrievalMs, rerankMs, usedPython, 0, 0, usedPython ? "hybrid" : "bm25",
                    null, "ok", "ok", "skipped");
        }

        public RetrievalResult(List<RetrievalHit> hits,
                               long retrievalMs,
                               long rerankMs,
                               boolean usedPython,
                               long embeddingMs,
                               long milvusMs,
                               String strategy,
                               String fallbackReason,
                               String embeddingStatus,
                               String milvusStatus,
                               String rerankStatus) {
            this.hits = hits;
            this.retrievalMs = retrievalMs;
            this.rerankMs = rerankMs;
            this.usedPython = usedPython;
            this.embeddingMs = embeddingMs;
            this.milvusMs = milvusMs;
            this.strategy = strategy;
            this.fallbackReason = fallbackReason;
            this.embeddingStatus = embeddingStatus;
            this.milvusStatus = milvusStatus;
            this.rerankStatus = rerankStatus;
        }

        public List<RetrievalHit> getHits() {
            return hits;
        }

        public long getRetrievalMs() {
            return retrievalMs;
        }

        public long getRerankMs() {
            return rerankMs;
        }

        public boolean isUsedPython() {
            return usedPython;
        }

        public long getEmbeddingMs() {
            return embeddingMs;
        }

        public long getMilvusMs() {
            return milvusMs;
        }

        public String getStrategy() {
            return strategy;
        }

        public String getFallbackReason() {
            return fallbackReason;
        }

        public String getEmbeddingStatus() {
            return embeddingStatus;
        }

        public String getMilvusStatus() {
            return milvusStatus;
        }

        public String getRerankStatus() {
            return rerankStatus;
        }
    }
}
