package com.aetherlearn.controller;

import com.aetherlearn.ai.AiConfig;
import com.aetherlearn.ai.PythonAiClient;
import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.Result;
import com.aetherlearn.dto.AiConfigUpdateRequest;
import com.aetherlearn.dto.AiConfigVO;
import com.aetherlearn.entity.SysConfig;
import com.aetherlearn.service.SysConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器（L7 sys_config 表接入）
 * <p>路径：/api/admin/config 。仅管理员可访问。</p>
 */
@RestController
@RequestMapping("/api/admin/config")
@PreAuthorize("hasRole('ADMIN')")
public class SysConfigController {

    private final SysConfigService sysConfigService;
    private final AiConfig aiConfig;
    private final PythonAiClient pythonAiClient;

    public SysConfigController(SysConfigService sysConfigService,
                               AiConfig aiConfig,
                               PythonAiClient pythonAiClient) {
        this.sysConfigService = sysConfigService;
        this.aiConfig = aiConfig;
        this.pythonAiClient = pythonAiClient;
    }

    /**
     * 获取所有配置列表
     */
    @GetMapping
    public Result<List<SysConfig>> listAll() {
        List<SysConfig> safeConfigs = sysConfigService.listAll().stream()
                .map(this::maskSensitiveValue)
                .toList();
        return Result.success(safeConfigs);
    }

    /**
     * 更新配置值
     *
     * @param key   配置键
     * @param value 配置值
     * @param remark 说明（可选）
     */
    @PutMapping("/{key}")
    public Result<Void> update(@PathVariable String key,
                               @RequestParam String value,
                               @RequestParam(required = false) String remark) {
        sysConfigService.updateValue(key, value, remark);
        return Result.success("配置已更新", null);
    }

    /**
     * 获取 AI 配置状态，不返回 API Key 原文。
     */
    @GetMapping("/ai")
    public Result<AiConfigVO> getAiConfig() {
        return Result.success(toAiConfigVO(aiConfig.current()));
    }

    /**
     * 保存 AI 动态配置，保存后下一次模型调用立即使用新值。
     */
    @PutMapping("/ai")
    public Result<AiConfigVO> updateAiConfig(@RequestBody AiConfigUpdateRequest request) {
        if (request.getEnabled() == null) {
            throw new BusinessException(400, "请选择是否启用 AI 服务");
        }
        String baseUrl = trim(request.getBaseUrl());
        String modelName = trim(request.getModelName());
        String apiKey = trim(request.getApiKey());
        if (request.getEnabled()) {
            if (baseUrl.isBlank()) {
                throw new BusinessException(400, "启用 AI 时必须填写 API 接口地址");
            }
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                throw new BusinessException(400, "API 接口地址必须以 http:// 或 https:// 开头");
            }
            if (modelName.isBlank()) {
                throw new BusinessException(400, "启用 AI 时必须填写模型名称");
            }
            if (apiKey.isBlank() && !aiConfig.current().apiKey().isBlank()) {
                // 留空代表保留已保存密钥。
                apiKey = "";
            } else if (apiKey.isBlank()) {
                throw new BusinessException(400, "启用 AI 时必须填写 API Key");
            }
        }
        sysConfigService.updateValue("ai.enabled", String.valueOf(request.getEnabled()),
                "是否启用大模型（true/false），关闭时走本地检索降级");
        sysConfigService.updateValue("ai.api.url", baseUrl,
                "大模型接口地址（OpenAI 兼容）");
        sysConfigService.updateValue("ai.model", modelName,
                "大模型名称");
        if (!apiKey.isBlank()) {
            sysConfigService.updateValue("ai.api.key", apiKey,
                    "大模型 API Key");
        }
        return Result.success("AI 配置已保存并立即生效", toAiConfigVO(aiConfig.current()));
    }

    /**
     * 使用当前已保存配置发起一次真实模型连接测试。
     * <p>由 Python AI 服务（LangGraph）执行真实调用；失败直接抛出原因，不做静默降级。</p>
     */
    @PostMapping("/ai/test")
    public Result<Map<String, String>> testAiConnection() {
        String response = pythonAiClient.testLlmConnection();
        return Result.success("模型连接成功", Map.of("response", response, "provider", "python"));
    }

    /** 将动态设置转换为不含密钥的视图。 */
    private AiConfigVO toAiConfigVO(AiConfig.Settings settings) {
        AiConfigVO vo = new AiConfigVO();
        vo.setEnabled(settings.enabled());
        vo.setBaseUrl(settings.baseUrl());
        vo.setModelName(settings.modelName());
        vo.setApiKeyConfigured(settings.apiKey() != null && !settings.apiKey().isBlank());
        vo.setAvailable(settings.available());
        if (!settings.enabled()) {
            vo.setStatusMessage("AI 服务已关闭，相关功能会使用本地规则或检索结果。");
        } else if (settings.available()) {
            vo.setStatusMessage("配置项完整，建议保存后执行连接测试。");
        } else {
            vo.setStatusMessage("AI 已启用，但接口地址、模型名称或 API Key 尚未配置完整。");
        }
        return vo;
    }

    /** 通用配置列表中的敏感项只返回掩码。 */
    private SysConfig maskSensitiveValue(SysConfig source) {
        SysConfig target = new SysConfig();
        target.setId(source.getId());
        target.setConfigKey(source.getConfigKey());
        target.setRemark(source.getRemark());
        String key = source.getConfigKey() == null ? "" : source.getConfigKey().toLowerCase();
        target.setConfigValue(key.contains("key") || key.contains("secret") || key.contains("password")
                ? (source.getConfigValue() == null || source.getConfigValue().isBlank() ? "" : "••••••••")
                : source.getConfigValue());
        return target;
    }

    /** 清理配置输入首尾空格。 */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
