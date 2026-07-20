package com.aetherlearn.ai;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * AI 大模型配置（F-QA / AI 模块）
 * <p>配置全部来自 Spring 配置文件：
 * <ul>
 *   <li>公开项（base-url / model / top-k 等）写在已提交 GitHub 的 {@code application.yml}；</li>
 *   <li>密钥 {@code ai.api-key} 仅写在 {@code application-local.yml}（已被 .gitignore 忽略，禁止入库）。</li>
 * </ul>
 * 这样既能把 API 配置直接放在配置文件里，又能保证密钥不上传 GitHub；缺失或关闭时
 * {@link #isAvailable()} 返回 false，问答自动降级为“仅知识库检索”。</p>
 */
@Slf4j
@Component
@Getter
public class AiConfig {

    /** 是否启用大模型（application.yml 的 ai.enabled） */
    @Value("${ai.enabled:true}")
    private boolean enabled;

    /** 供应商基址，如 https://openrouter.ai（公开，可入库） */
    @Value("${ai.base-url:}")
    private String baseUrl;

    /** API Key（仅存于 gitignored 的 application-local.yml，禁止入库） */
    @Value("${ai.api-key:}")
    private String apiKey;

    /** 模型名称（公开，可入库） */
    @Value("${ai.model:}")
    private String model;

    @PostConstruct
    public void init() {
        if (isAvailable()) {
            log.info("[AI] 配置就绪：baseUrl={}, model={}（已启用大模型）", baseUrl, model);
        } else if (!enabled) {
            log.warn("[AI] 已在配置中关闭（ai.enabled=false），将走本地检索降级。");
        } else {
            log.warn("[AI] 未配置 ai.api-key（或 base-url/model 缺失），AI 生成不可用，问答将降级为仅知识库检索。");
        }
    }

    /** AI 是否真正可用（启用且 base-url / api-key / model 三项齐全） */
    public boolean isAvailable() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }

    /** 拼接 OpenAI 兼容的 chat/completions 端点 */
    public String getChatCompletionsUrl() {
        String u = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return u + "/api/v1/chat/completions";
    }
}
