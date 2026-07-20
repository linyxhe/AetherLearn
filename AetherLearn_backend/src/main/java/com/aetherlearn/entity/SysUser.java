package com.aetherlearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户实体（F-AUTH 用户与权限管理）
 * <p>对应表 {@code sys_user}，密码为 MD5 密文，角色 1-管理员 2-教师 3-学生。</p>
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    /** 用户ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码（MD5 密文） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色：1-管理员 2-教师 3-学生 */
    private Integer role;

    /** 头像路径（uploads/avatar） */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 状态：1-正常 0-禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
