package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.entity.SysConfig;
import com.aetherlearn.service.SysConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置控制器（L7 sys_config 表接入）
 * <p>路径：/api/admin/config 。仅管理员可访问。</p>
 */
@RestController
@RequestMapping("/api/admin/config")
@PreAuthorize("hasRole('ADMIN')")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 获取所有配置列表
     */
    @GetMapping
    public Result<List<SysConfig>> listAll() {
        return Result.success(sysConfigService.listAll());
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
}
