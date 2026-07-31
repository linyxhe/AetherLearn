package com.aetherlearn.ai;

import com.aetherlearn.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 动态配置读取器。
 * <p>管理员在系统配置页保存的 {@code sys_config} 值优先于 application.yml，
 * 因此模型、接口地址与 API Key 无需重启服务即可生效。</p>
 */
@Slf4j
@Component
public class AiConfig {

    private final SysConfigService sysConfigService;

    /** application.yml 中的兜底开关。 */
    @Value("${ai.enabled:true}")
    private boolean defaultEnabled;

    /** application.yml 中的兜底接口地址。 */
    @Value("${langchain4j.open-ai.chat-model.base-url:}")
    private String defaultBaseUrl;

    /** application.yml 中的兜底 API Key。 */
    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String defaultApiKey;

    /** application.yml 中的兜底模型名称。 */
    @Value("${langchain4j.open-ai.chat-model.model-name:}")
    private String defaultModelName;

    /** application.yml 中的兜底温度。 */
    @Value("${langchain4j.open-ai.chat-model.temperature:0.3}")
    private double defaultTemperature;

    public AiConfig(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 获取当前实时配置快照。
     *
     * @return 可直接用于创建模型客户端的配置
     */
    public Settings current() {
        String enabledValue = databaseValue("ai.enabled");
        boolean enabled = enabledValue == null || enabledValue.isBlank()
                ? defaultEnabled : Boolean.parseBoolean(enabledValue);
        String baseUrl = valueOrDefault("ai.api.url", defaultBaseUrl);
        String apiKey = valueOrDefault("ai.api.key", defaultApiKey);
        String modelName = valueOrDefault("ai.model", defaultModelName);
        return new Settings(enabled, trim(baseUrl), trim(apiKey), trim(modelName), defaultTemperature);
    }

    /**
     * 判断 AI 是否具备调用条件。
     *
     * @return true 表示已启用且必填配置完整
     */
    public boolean isAvailable() {
        return current().available();
    }

    /** 读取数据库配置；数据库临时不可用时退回启动配置，保证非 AI 功能可运行。 */
    private String databaseValue(String key) {
        try {
            return sysConfigService.getValueByKey(key);
        } catch (RuntimeException ex) {
            log.warn("[AI] 读取动态配置 {} 失败，使用 application.yml 兜底：{}", key, ex.getMessage());
            return null;
        }
    }

    /** 数据库值为空时使用 application.yml 的默认值。 */
    private String valueOrDefault(String key, String defaultValue) {
        String value = databaseValue(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** 统一清理配置首尾空格。 */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * AI 配置快照。
     *
     * @param enabled 是否启用
     * @param baseUrl OpenAI 兼容接口地址
     * @param apiKey API Key
     * @param modelName 模型名称
     * @param temperature 生成温度
     */
    public record Settings(boolean enabled,
                           String baseUrl,
                           String apiKey,
                           String modelName,
                           double temperature) {

        /** 配置是否完整可用。 */
        public boolean available() {
            return enabled
                    && baseUrl != null && !baseUrl.isBlank()
                    && apiKey != null && !apiKey.isBlank()
                    && modelName != null && !modelName.isBlank();
        }

        /** 用于判断模型客户端是否需要重建。 */
        public String signature() {
            return enabled + "\n" + baseUrl + "\n" + apiKey + "\n" + modelName + "\n" + temperature;
        }
    }
}
