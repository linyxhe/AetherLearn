package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 系统配置实体（L7 sys_config 表接入）
 * <p>对应表 {@code sys_config}，存储系统级配置键值对。</p>
 */
@Data
@TableName("sys_config")
public class SysConfig implements Serializable {

    /** 配置ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键（唯一） */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 说明 */
    private String remark;
}
