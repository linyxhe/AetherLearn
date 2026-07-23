package com.aetherlearn.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 大模型调用客户端（基于 LangChain4j 框架）
 * <p>统一处理流式/同步调用，支持所有 OpenAI 兼容供应商（小米 MiMo / DeepSeek / OpenRouter 等）。
 * 流式模型可选注入；不支持时自动降级为同步调用。</p>
 */
@Slf4j
@Component
public class LlmClient {

    /** LangChain4j 同步模型（自动注入） */
    private final ChatModel chatModel;
    /** LangChain4j 流式模型（可选注入，不支持时为 null） */
    private final StreamingChatModel streamingChatModel;
    /** AI 业务配置 */
    private final AiConfig aiConfig;

    public LlmClient(ChatModel chatModel,
                     AiConfig aiConfig,
                     @Autowired(required = false) StreamingChatModel streamingChatModel) {
        this.chatModel = chatModel;
        this.aiConfig = aiConfig;
        this.streamingChatModel = streamingChatModel;
        if (streamingChatModel != null) {
            log.info("[LLM] 流式模型已注入，支持 SSE 流式输出");
        } else {
            log.info("[LLM] 流式模型未注入，将使用同步调用（通过 SSE 一次性返回）");
        }
    }

    /**
     * 同步调用：一次返回完整回答
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!aiConfig.isAvailable()) {
            return null;
        }
        try {
            ChatResponse response = chatModel.chat(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );
            AiMessage ai = response.aiMessage();
            return (ai != null && ai.text() != null && !ai.text().isBlank())
                    ? ai.text().trim() : null;
        } catch (Exception e) {
            log.error("[LLM] 同步调用失败", e);  // ← 改为 error + 完整堆栈
            return null;
        }
    }

    /**
     * 同步调用（带业务级短超时）
     * <p>用于页面型功能，避免用户打开页面时被大模型网络耗时卡住。</p>
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
            log.warn("[LLM] 短超时调用失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 流式调用：逐 chunk 回调
     * <p>有 StreamingChatLanguageModel 时走原生流式；否则降级为同步调用一次性返回。</p>
     */
    public boolean chatStream(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        if (!aiConfig.isAvailable()) {
            return false;
        }

        // 无流式模型 → 降级为同步
        if (streamingChatModel == null) {
            return chatFallback(systemPrompt, userPrompt, onChunk);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StringBuilder> result = new AtomicReference<>(new StringBuilder());
        AtomicReference<Throwable> error = new AtomicReference<>();

        try {
            streamingChatModel.chat(
                    List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userPrompt)
                    ),
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
                        public void onError(Throwable t) {
                            error.set(t);
                            latch.countDown();
                        }
                    }
            );

            boolean completed = latch.await(120, TimeUnit.SECONDS);

            if (error.get() != null) {
                log.warn("[LLM] 流式调用异常：{}", error.get().getMessage());
                return chatFallback(systemPrompt, userPrompt, onChunk);
            }

            if (!completed) {
                log.warn("[LLM] 流式调用超时");
                return chatFallback(systemPrompt, userPrompt, onChunk);
            }

            return result.get().length() > 0;
        } catch (Exception e) {
            log.error("[LLM] 流式调用异常，降级为同步：", e);  // ← 完整堆栈
            return chatFallback(systemPrompt, userPrompt, onChunk);
        }
    }

    /** 同步降级 */
    private boolean chatFallback(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        String result = chat(systemPrompt, userPrompt);
        if (result != null) {
            onChunk.accept(result);
            return true;
        }
        return false;
    }
}
