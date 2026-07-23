package com.aetherlearn.service;

import com.aetherlearn.entity.SysConfig;

import java.util.List;

/**
 * 系统配置服务接口（L7 sys_config 表接入）
 * <p>提供配置的 CRUD 和按 key 查询功能，供管理员使用。</p>
 */
public interface SysConfigService {

    /**
     * 获取所有配置列表
     */
    List<SysConfig> listAll();

    /**
     * 按 key 获取配置值
     *
     * @param configKey 配置键
     * @return 配置值，不存在时返回 null
     */
    String getValueByKey(String configKey);

    /**
     * 更新配置值（不存在则创建）
     *
     * @param configKey   配置键
     * @param configValue 配置值
     * @param remark      说明（可选）
     */
    void updateValue(String configKey, String configValue, String remark);
}
