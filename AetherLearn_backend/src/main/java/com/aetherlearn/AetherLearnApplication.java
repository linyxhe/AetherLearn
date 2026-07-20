package com.aetherlearn;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AetherLearn 后端启动类（Spring Boot 3.4.x）
 * <p>
 * 模块归属：系统启动（F-AUTH / 基础支撑）
 * 通过 {@code @MapperScan} 扫描 MyBatis-Plus 的 Mapper 接口。
 * </p>
 */
@SpringBootApplication
@MapperScan("com.aetherlearn.mapper")
public class AetherLearnApplication {

    public static void main(String[] args) {
        SpringApplication.run(AetherLearnApplication.class, args);
    }
}
