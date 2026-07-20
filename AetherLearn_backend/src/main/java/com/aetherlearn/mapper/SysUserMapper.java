package com.aetherlearn.mapper;

import com.aetherlearn.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper（F-AUTH 用户与权限管理）
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper} 获得基础 CRUD。</p>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按登录账号查询用户（用于登录校验） */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND status = 1")
    SysUser selectByUsername(String username);
}
