package com.aetherlearn.ai;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * AI 业务配置（F-QA / AI 模块）
 * <p>大模型调用已迁移到 LangChain4j 框架，API Key / base-url / model 等由
 * {@code langchain4j.open-ai.chat-model.*} 统一管理。</p>
 * <p>本类仅保留业务层配置：是否启用、RAG 检索参数等。</p>
 */
@Slf4j
@Component
@Getter
public class AiConfig {

    /** 是否启用大模型（application.yml 的 ai.enabled） */
    @Value("${ai.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("[AI] 大模型已启用（LangChain4j 管理连接，配置见 langchain4j.open-ai.chat-model.*）");
        } else {
            log.warn("[AI] 已在配置中关闭（ai.enabled=false），将走本地检索降级。");
        }
    }

    /** AI 是否真正可用（仅检查 enabled 开关；LangChain4j 配置缺失时会在启动期报错） */
    public boolean isAvailable() {
        return enabled;
    }
}
