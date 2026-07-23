package com.aetherlearn.mapper;

import com.aetherlearn.entity.SysConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置 Mapper（L7 sys_config 表接入）
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
