package com.aetherlearn.service.impl;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.entity.SysConfig;
import com.aetherlearn.mapper.SysConfigMapper;
import com.aetherlearn.service.SysConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统配置服务实现（L7 sys_config 表接入）
 */
@Slf4j
@Service
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    public SysConfigServiceImpl(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    @Override
    public List<SysConfig> listAll() {
        return sysConfigMapper.selectList(null);
    }

    @Override
    public String getValueByKey(String configKey) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, configKey);
        SysConfig config = sysConfigMapper.selectOne(wrapper);
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateValue(String configKey, String configValue, String remark) {
        if (configKey == null || configKey.isBlank()) {
            throw new BusinessException(400, "配置键不能为空");
        }
        // 查找已有配置
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, configKey);
        SysConfig existing = sysConfigMapper.selectOne(wrapper);

        if (existing != null) {
            // 更新
            existing.setConfigValue(configValue);
            if (remark != null && !remark.isBlank()) {
                existing.setRemark(remark);
            }
            sysConfigMapper.updateById(existing);
            log.info("[Config] 更新配置：key={}, value={}", configKey, safeLogValue(configKey, configValue));
        } else {
            // 新增
            SysConfig config = new SysConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(configValue);
            config.setRemark(remark != null ? remark : configKey);
            sysConfigMapper.insert(config);
            log.info("[Config] 新增配置：key={}, value={}", configKey, safeLogValue(configKey, configValue));
        }
    }

    /** 敏感配置不得以明文写入应用日志。 */
    private String safeLogValue(String key, String value) {
        String normalized = key == null ? "" : key.toLowerCase();
        return normalized.contains("key") || normalized.contains("secret") || normalized.contains("password")
                ? "***已更新***" : value;
    }
}
