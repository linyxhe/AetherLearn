package com.aetherlearn.mapper;

import com.aetherlearn.entity.KnowledgeDoc;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper（F-KB 知识库模块）
 * <p>继承 {@code BaseMapper} 获得基础 CRUD；软删除由全局逻辑删除自动处理。</p>
 */
@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {
}
