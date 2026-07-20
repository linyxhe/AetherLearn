package com.aetherlearn.config;

import com.aetherlearn.common.JwtUtil;
import com.aetherlearn.common.LoginUser;
import com.aetherlearn.common.RoleConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 认证过滤器（F-AUTH-04 角色与权限拦截）
 * <p>拦截除登录外的请求：解析 Authorization 头中的 JWT，
 * 校验通过后构造 {@link LoginUser} 注入 SecurityContext，供后续鉴权使用。
 * 校验失败（缺失/非法/过期）直接返回 401 JSON 并短路。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 无需鉴权的白名单（登录、文档、静态资源） */
    private static final String[] WHITE_LIST = {
            "/api/auth/login",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/doc.html",
            "/uploads/**"
    };

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 1. 白名单直接放行
        for (String w : WHITE_LIST) {
            if (pathMatcher.match(w, path)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 2. 取 Token
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            writeUnauthorized(response, "缺少 Authorization 头");
            return;
        }

        // 3. 解析并校验
        try {
            String raw = jwtUtil.extractRawToken(header);
            var claims = jwtUtil.parseToken(raw);

            Long userId = jwtUtil.getUserId(claims);
            String username = jwtUtil.getUsername(claims);
            Integer role = jwtUtil.getRole(claims);

            // 4. 构造认证信息并注入上下文
            LoginUser loginUser = new LoginUser(userId, username, role);
            var authorities = Collections.singletonList(
                    new SimpleGrantedAuthority(RoleConstant.toAuthority(role)));
            var authentication = new UsernamePasswordAuthenticationToken(
                    loginUser, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            writeUnauthorized(response, "Token 非法或已过期");
        }
    }

    /** 写出 401 JSON（过滤器层无法被 @RestControllerAdvice 捕获，故手动写） */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", 401);
        body.put("message", msg);
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
