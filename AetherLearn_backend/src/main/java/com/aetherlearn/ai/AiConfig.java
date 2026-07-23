package com.aetherlearn.ai;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * AI 业务配置（F-QA / AI 模块）
 * <p>大模型调用已迁移到 LangChain4j 框架，API Key / base-url / model 等由
 * {@code langchain4j.open-ai.chat-model.*} 统一管理。</p>
 * <p>本类仅保留业务层配置：是否启用、RAG 检索参数等，以及手动注入 StreamingChatModel。</p>
 */
@Slf4j
@Configuration
@Getter
public class AiConfig {

    /** 是否启用大模型（application.yml 的 ai.enabled） */
    @Value("${ai.enabled:true}")
    private boolean enabled;

    @Value("${langchain4j.open-ai.chat-model.base-url:}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.3}")
    private double temperature;

    @PostConstruct
    public void init() {
        log.info("[AI] 配置加载: enabled={}, baseUrl={}, modelName={}, apiKey={}",
                enabled, baseUrl, modelName,
                apiKey != null && !apiKey.isBlank() ? "***已配置***" : "***未配置***");
        if (enabled) {
            log.info("[AI] 大模型已启用（LangChain4j 管理连接，配置见 langchain4j.open-ai.chat-model.*）");
        } else {
            log.warn("[AI] 已在配置中关闭（ai.enabled=false），将走本地检索降级。");
        }
    }

    /** AI 是否真正可用：必须启用且配置 base-url / api-key / model。 */
    public boolean isAvailable() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && modelName != null && !modelName.isBlank();
    }

    /**
     * 手动注入流式模型（LangChain4j beta 版自动配置可能不注入 StreamingChatModel）
     */
    @Bean
    public StreamingChatModel streamingChatModel() {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("[AI] 缺少 base-url 或 api-key，无法创建 StreamingChatModel");
            return null;
        }
        log.info("[AI] 手动创建 StreamingChatModel: baseUrl={}, model={}, temperature={}", baseUrl, modelName, temperature);
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(120))
                .build();
    }
}
