package com.aetherlearn.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 大模型调用客户端（F-QA 智能答疑 / AI 模块）
 * <p>通过 OpenAI 兼容的 chat/completions 接口调用大模型（支持 OpenAI / OpenRouter / Ollama 等）。
 * 调用失败时返回 null，由上层 {@code QaService} 降级为“仅知识库检索”，保证无 Key / 离线可演示。</p>
 */
@Slf4j
@Component
public class LlmClient {

    /** AI 配置（来自 application.yml / application-local.yml） */
    private final AiConfig aiConfig;
    /** 同步 REST 客户端 */
    private final RestTemplate restTemplate;
    /** JSON 解析 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmClient(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
        // 设置连接与读取超时，避免大模型响应慢时长时间阻塞主线程
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 发起一次对话补全
     *
     * @param systemPrompt 系统提示词（角色与约束）
     * @param userPrompt   用户问题 / 带上下文的提示
     * @return 模型回复文本；失败返回 null（触发降级）
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!aiConfig.isAvailable()) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiConfig.getApiKey());

            // 构造 OpenAI 兼容请求体
            Map<String, Object> body = Map.of(
                    "model", aiConfig.getModel(),
                    "temperature", 0.3,
                    "stream", false,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    aiConfig.getChatCompletionsUrl(), entity, String.class);

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[LLM] 调用返回非 2xx：{}", resp.getStatusCode());
                return null;
            }
            // 解析 choices[0].message.content
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
            log.warn("[LLM] 返回结构异常：{}", resp.getBody());
            return null;
        } catch (RestClientException e) {
            // 网络异常（超时/不可达/无 Key）直接降级
            log.warn("[LLM] 网络调用失败，降级为仅检索：{}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("[LLM] 解析响应失败，降级为仅检索：{}", e.getMessage());
            return null;
        }
    }
}
