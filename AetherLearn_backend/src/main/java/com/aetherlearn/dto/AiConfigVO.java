package com.aetherlearn.dto;

import lombok.Data;

/**
 * 管理端 AI 配置视图。
 * <p>仅返回 API Key 是否已配置，不向浏览器返回密钥原文。</p>
 */
@Data
public class AiConfigVO {

    /** 是否启用 AI 服务。 */
    private boolean enabled;

    /** OpenAI 兼容接口地址。 */
    private String baseUrl;

    /** 模型名称。 */
    private String modelName;

    /** 是否已有可用 API Key。 */
    private boolean apiKeyConfigured;

    /** 当前必填配置是否完整。 */
    private boolean available;

    /** 面向管理员的状态说明。 */
    private String statusMessage;
}
