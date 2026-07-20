package com.aetherlearn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 配置（F-FILE-02 虚拟路径访问）
 * <p>将 HTTP 路径 {@code /uploads/**} 映射到本地磁盘的 uploads 目录，实现上传文件的可访问。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 上传根目录（来自 application.yml 的 file.upload-dir） */
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将磁盘绝对路径作为资源位置（file: 前缀）
        Path absDir = Paths.get(uploadDir).toAbsolutePath();
        String location = "file:" + absDir.toString().replace("\\", "/") + "/";

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
