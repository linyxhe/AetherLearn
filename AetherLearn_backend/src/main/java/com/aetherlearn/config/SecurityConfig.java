package com.aetherlearn.config;

import com.aetherlearn.common.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

/**
 * Spring Security 配置（F-AUTH-04 角色与权限拦截）
 * <p>核心：无状态（JWT）、关闭 CSRF、注册 JWT 过滤器、开启方法级 @PreAuthorize 鉴权、配置 CORS。</p>
 */
@Configuration
@EnableMethodSecurity   // 开启 @PreAuthorize / @PostAuthorize 方法级鉴权（Spring Boot 3）
public class SecurityConfig {

    private final JwtUtil jwtUtil;

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
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
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
        config.setAllowedOriginPatterns(List.of("*"));   // 开发期放开；生产请限定具体域名
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
