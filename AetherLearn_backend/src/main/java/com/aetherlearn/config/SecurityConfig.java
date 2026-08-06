package com.aetherlearn.config;

import com.aetherlearn.common.JwtUtil;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Arrays;

/**
 * Spring Security 配置（F-AUTH-04 角色与权限拦截）
 * <p>核心：无状态（JWT）、关闭 CSRF、注册 JWT 过滤器、开启方法级 @PreAuthorize 鉴权、配置 CORS。</p>
 */
@Configuration
@EnableMethodSecurity   // 开启 @PreAuthorize / @PostAuthorize 方法级鉴权（Spring Boot 3）
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    /** 允许跨域的前端来源，由开发/生产环境配置分别提供。 */
    @Value("${aetherlearn.cors.allowed-origin-patterns}")
    private String allowedOriginPatterns;

    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * 安全过滤链
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 关闭 CSRF（JWT 无状态，无需防跨站）
                .csrf(AbstractHttpConfigurer::disable)
                // 允许课程 PDF 等上传资料在前端学习页 iframe 中预览
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                // 无状态会话
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 所有请求先经 JWT 过滤器鉴权；方法级 @PreAuthorize 再校验角色
                .authorizeHttpRequests(auth -> auth
                        // SSE 响应写出时会触发 ASYNC 二次分派；初始请求已完成 JWT 和角色校验，
                        // 此处仅放行服务器内部的异步/错误分派，避免已提交响应再次被拦截为 403。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/login", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**", "/doc.html", "/uploads/**",
                                "/", "/index.html", "/favicon.png", "/favicon.ico", "/assets/**",
                                "/login", "/dashboard", "/student-dashboard", "/ai-advice", "/paper-builder",
                                "/course", "/user", "/config", "/knowledge", "/homework", "/notice",
                                "/knowledge-graph", "/my-course", "/my-notes", "/my-homework", "/wrong-book",
                                "/todo", "/ai-report", "/profile", "/qa",
                                "/aetherlearn", "/aetherlearn/", "/aetherlearn/index.html",
                                "/aetherlearn/favicon.png", "/aetherlearn/favicon.ico", "/aetherlearn/assets/**",
                                "/aetherlearn/uploads/**", "/aetherlearn/api/auth/login",
                                "/aetherlearn/swagger-ui.html", "/aetherlearn/swagger-ui/**",
                                "/aetherlearn/v3/api-docs/**", "/aetherlearn/doc.html",
                                "/aetherlearn/login", "/aetherlearn/dashboard", "/aetherlearn/student-dashboard",
                                "/aetherlearn/ai-advice", "/aetherlearn/paper-builder", "/aetherlearn/course",
                                "/aetherlearn/user", "/aetherlearn/config", "/aetherlearn/knowledge",
                                "/aetherlearn/homework", "/aetherlearn/notice", "/aetherlearn/knowledge-graph",
                                "/aetherlearn/my-course", "/aetherlearn/my-notes", "/aetherlearn/my-homework",
                                "/aetherlearn/wrong-book", "/aetherlearn/todo", "/aetherlearn/ai-report",
                                "/aetherlearn/profile", "/aetherlearn/qa").permitAll()
                        .anyRequest().authenticated())
                // 注册自定义 JWT 过滤器（置于用户名密码过滤器之前）
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
                // 跨域
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    /**
     * CORS 配置：允许前端（Vite 默认 5173）跨域调用后端
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 密码编码器（本系统登录采用 MD5 比对，此 Bean 供 Spring Security 体系兼容使用）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
