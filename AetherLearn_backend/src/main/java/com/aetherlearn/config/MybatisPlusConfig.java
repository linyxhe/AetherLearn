package com.aetherlearn.config;

import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置（基础支撑）
 * <p>
 * 说明：本波次（登录鉴权 / 课程 CRUD / 文件上传）暂不使用分页，故此处不注册任何插件即可跑通。
 * 后续（看板 / 列表分页）若需分页，MyBatis-Plus 3.5.9 起分页插件 {@code PaginationInnerInterceptor}
 * 已移至独立模块，需先在 pom.xml 引入：
 * <pre>
 *   &lt;dependency&gt;
 *     &lt;groupId&gt;com.baomidou&lt;/groupId&gt;
 *     &lt;artifactId&gt;mybatis-plus-jsqlparser&lt;/artifactId&gt;
 *     &lt;version&gt;3.5.9&lt;/version&gt;
 *   &lt;/dependency&gt;
 * </pre>
 * 再在此处注册 {@code new PaginationInnerInterceptor(DbType.MYSQL)} 即可。
 * </p>
 */
@Configuration
public class MybatisPlusConfig {
    // 预留：分页等插件在此注册（见类注释说明）
}
