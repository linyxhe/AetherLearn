package com.aetherlearn.ai;

import com.aetherlearn.common.BusinessException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 大模型调用客户端（基于 LangChain4j 框架）。
 * <p>每次调用都会读取动态配置快照；当模型、地址或 API Key 变化时自动重建客户端，
 * 使管理端保存的新配置立即生效。</p>
 */
@Slf4j
@Component
public class LlmClient {

    private final AiConfig aiConfig;
    private volatile ModelBundle cachedModels;

    public LlmClient(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    /**
     * 同步调用：一次返回完整回答，失败时返回 null 以触发业务降级。
     */
    public String chat(String systemPrompt, String userPrompt) {
        AiConfig.Settings settings = aiConfig.current();
        if (!settings.available()) {
            return null;
        }
        try {
            ChatResponse response = models(settings).chatModel().chat(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );
            AiMessage ai = response.aiMessage();
            return ai != null && ai.text() != null && !ai.text().isBlank()
                    ? ai.text().trim() : null;
        } catch (Exception e) {
            log.error("[LLM] 同步调用失败：{}", rootMessage(e), e);
            return null;
        }
    }

    /**
     * 同步调用（带业务级短超时）。
     */
    public String chatWithin(String systemPrompt, String userPrompt, long timeoutSeconds) {
        if (!aiConfig.isAvailable()) {
            return null;
        }
        try {
            return CompletableFuture
                    .supplyAsync(() -> chat(systemPrompt, userPrompt))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("[LLM] 短超时调用超过 {} 秒，返回本地规则结果", timeoutSeconds);
            return null;
        } catch (Exception e) {
            log.warn("[LLM] 短超时调用失败：{}", rootMessage(e));
            return null;
        }
    }

    /**
     * 测试当前动态配置是否能得到模型响应。
     *
     * @return 模型返回的简短响应
     */
    public String testConnection() {
        AiConfig.Settings settings = aiConfig.current();
        validateSettings(settings);
        try {
            ChatResponse response = models(settings).chatModel().chat(
                    SystemMessage.from("你是连接测试助手。"),
                    UserMessage.from("只回复：连接成功")
            );
            String text = response.aiMessage() == null ? null : response.aiMessage().text();
            if (text == null || text.isBlank()) {
                throw new BusinessException(502, "模型已连接，但没有返回内容，请检查模型是否支持对话接口");
            }
            return text.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[LLM] 管理端连接测试失败：{}", rootMessage(e), e);
            throw new BusinessException(502, friendlyError(e));
        }
    }

    /**
     * 流式调用：逐块回调，流式失败时自动降级为同步调用。
     */
    public boolean chatStream(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        AiConfig.Settings settings = aiConfig.current();
        if (!settings.available()) {
            return false;
        }
        StreamingChatModel streamingChatModel;
        try {
            streamingChatModel = models(settings).streamingChatModel();
        } catch (Exception e) {
            log.warn("[LLM] 创建流式模型失败：{}", rootMessage(e));
            return chatFallback(systemPrompt, userPrompt, onChunk);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StringBuilder> result = new AtomicReference<>(new StringBuilder());
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            streamingChatModel.chat(
                    List.of(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt)),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String token) {
                            result.get().append(token);
                            onChunk.accept(token);
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            error.set(throwable);
                            latch.countDown();
                        }
                    }
            );
            boolean completed = latch.await(120, TimeUnit.SECONDS);
            if (error.get() != null || !completed) {
                log.warn("[LLM] 流式调用未完成：{}",
                        error.get() == null ? "等待超时" : rootMessage(error.get()));
                return chatFallback(systemPrompt, userPrompt, onChunk);
            }
            return result.get().length() > 0;
        } catch (Exception e) {
            log.error("[LLM] 流式调用异常，降级为同步：{}", rootMessage(e), e);
            return chatFallback(systemPrompt, userPrompt, onChunk);
        }
    }

    /** 根据配置签名复用或重建模型客户端。 */
    private ModelBundle models(AiConfig.Settings settings) {
        String signature = settings.signature();
        ModelBundle current = cachedModels;
        if (current != null && current.signature().equals(signature)) {
            return current;
        }
        synchronized (this) {
            current = cachedModels;
            if (current != null && current.signature().equals(signature)) {
                return current;
            }
            ChatModel chatModel = OpenAiChatModel.builder()
                    .baseUrl(settings.baseUrl())
                    .apiKey(settings.apiKey())
                    .modelName(settings.modelName())
                    .temperature(settings.temperature())
                    .timeout(Duration.ofSeconds(120))
                    .build();
            StreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                    .baseUrl(settings.baseUrl())
                    .apiKey(settings.apiKey())
                    .modelName(settings.modelName())
                    .temperature(settings.temperature())
                    .timeout(Duration.ofSeconds(120))
                    .build();
            cachedModels = new ModelBundle(signature, chatModel, streamingChatModel);
            log.info("[LLM] 动态配置已生效：baseUrl={}, model={}",
                    settings.baseUrl(), settings.modelName());
            return cachedModels;
        }
    }

    /** 校验连接测试所需配置。 */
    private void validateSettings(AiConfig.Settings settings) {
        if (!settings.enabled()) {
            throw new BusinessException(400, "请先启用 AI 服务再测试连接");
        }
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()) {
            throw new BusinessException(400, "请填写 API 接口地址");
        }
        if (!settings.baseUrl().startsWith("http://") && !settings.baseUrl().startsWith("https://")) {
            throw new BusinessException(400, "API 接口地址必须以 http:// 或 https:// 开头");
        }
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new BusinessException(400, "请填写 API Key");
        }
        if (settings.modelName() == null || settings.modelName().isBlank()) {
            throw new BusinessException(400, "请填写 AI 模型名称");
        }
    }

    /** 同步降级。 */
    private boolean chatFallback(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        String result = chat(systemPrompt, userPrompt);
        if (result == null) {
            return false;
        }
        onChunk.accept(result);
        return true;
    }

    /** 将第三方异常转换为管理员可以直接处理的提示。 */
    private String friendlyError(Throwable throwable) {
        String message = rootMessage(throwable).toLowerCase(Locale.ROOT);
        if (message.contains("401") || message.contains("unauthorized")
                || message.contains("invalid api key") || message.contains("authentication")) {
            return "API Key 无效或已过期，请检查后重新保存";
        }
        if (message.contains("404") || message.contains("model_not_found")
                || message.contains("model not found")) {
            return "未找到该模型，请检查模型名称和 API 接口地址";
        }
        if (message.contains("429") || message.contains("rate limit")
                || message.contains("quota")) {
            return "模型接口限流或额度不足，请稍后重试或检查账户余额";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "连接模型超时，请检查网络和 API 接口地址";
        }
        if (message.contains("connection") || message.contains("unknown host")
                || message.contains("refused")) {
            return "无法连接 API 接口，请检查地址和网络状态";
        }
        return "模型连接失败：" + rootMessage(throwable);
    }

    /** 提取最内层异常消息，避免提示被包装异常淹没。 */
    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /** 同一份动态配置对应的同步与流式客户端。 */
    private record ModelBundle(String signature,
                               ChatModel chatModel,
                               StreamingChatModel streamingChatModel) {
    }
}
