package com.aetherlearn.dto;

import lombok.Data;

/**
 * 管理端 AI 配置更新请求。
 * <p>API Key 留空表示保留现有值，避免管理端回显敏感信息。</p>
 */
@Data
public class AiConfigUpdateRequest {

    /** 是否启用 AI 服务。 */
    private Boolean enabled;

    /** OpenAI 兼容接口地址。 */
    private String baseUrl;

    /** 模型名称。 */
    private String modelName;

    /** 新 API Key；留空时不修改。 */
    private String apiKey;
}
