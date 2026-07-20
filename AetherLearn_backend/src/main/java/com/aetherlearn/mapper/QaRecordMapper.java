package com.aetherlearn.mapper;

import com.aetherlearn.entity.QaRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能问答记录 Mapper（F-QA 智能答疑模块）
 * <p>继承 {@code BaseMapper} 获得基础 CRUD。</p>
 */
@Mapper
public interface QaRecordMapper extends BaseMapper<QaRecord> {
}
