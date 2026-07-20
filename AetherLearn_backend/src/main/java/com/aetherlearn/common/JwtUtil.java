package com.aetherlearn.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类（F-AUTH-01 登录接口）
 * <p>负责生成与解析 Token。Token 内承载用户关键身份：userId、username、role。</p>
 */
@Component
public class JwtUtil {

    /** 密钥（来自 application.yml 的 jwt.secret） */
    @Value("${jwt.secret}")
    private String secret;

    /** 有效期（毫秒，来自 application.yml 的 jwt.expiration） */
    @Value("${jwt.expiration}")
    private Long expiration;

    /** Token 前缀（来自 application.yml 的 jwt.token-prefix） */
    @Value("${jwt.token-prefix}")
    private String tokenPrefix;

    /** 自定义载荷键名 */
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    /** 生成 SecretKey（jjwt 0.12.x 要求至少 256 位密钥） */
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     *
     * @param userId   用户ID
     * @param username 登录账号
     * @param role     角色编码（1/2/3）
     * @return 形如 "Bearer xxxxx" 的完整 Token 串
     */
    public String generateToken(Long userId, String username, Integer role) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_USERNAME, username);
        claims.put(CLAIM_ROLE, role);

        String token = Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(getKey())
                .compact();

        return tokenPrefix + token;
    }

    /** 去掉前缀（Bearer  ）得到裸 Token */
    public String extractRawToken(String header) {
        if (header != null && header.startsWith(tokenPrefix)) {
            return header.substring(tokenPrefix.length());
        }
        return header;
    }

    /** 解析 Token，返回 Claims */
    public Claims parseToken(String rawToken) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(rawToken)
                .getPayload();
    }

    /** 从 Claims 中取 userId */
    public Long getUserId(Claims claims) {
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    /** 从 Claims 中取 username */
    public String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    /** 从 Claims 中取 role */
    public Integer getRole(Claims claims) {
        return claims.get(CLAIM_ROLE, Integer.class);
    }
}
