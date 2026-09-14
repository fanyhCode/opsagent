package com.opsagent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌服务：负责"签发令牌"和"解析令牌"。
 *
 * JWT 长什么样？
 *   头部.载荷.签名   （三部分用点分隔，例如 eyJhbGciOi...）
 * 其中：
 * - 载荷（payload）里放的是明文信息，例如用户 id、用户名、角色、过期时间，
 *   任何人都能解开看，所以绝不能放密码等敏感信息；
 * - 签名（signature）是用密钥对前两部分做的加密摘要，别人改一个字符签名就对不上，
 *   因此令牌无法被伪造——这就是"无状态认证"的基础：服务器不用保存会话，
 *   每次请求带着令牌来，服务器验签即可确认身份。
 */
@Service
public class JwtService {

    /** 签名密钥，从 application.yml 里读取 */
    private final SecretKey secretKey;

    /** 令牌有效期（分钟） */
    private final long expireMinutes;

    public JwtService(@Value("${opsagent.jwt.secret}") String secret,
                      @Value("${opsagent.jwt.expire-minutes}") long expireMinutes) {
        // HS256 要求密钥至少 256 位，Keys.hmacShaKeyFor 会做长度校验
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    /**
     * 签发令牌。登录成功后调用。
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMinutes * 60 * 1000);

        return Jwts.builder()
                .subject(String.valueOf(userId))   // sub：主体，这里放用户 id
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)                     // iat：签发时间
                .expiration(expiration)            // exp：过期时间
                .signWith(secretKey)               // 用密钥签名
                .compact();
    }

    /**
     * 解析并校验令牌。
     * 如果令牌被篡改、已过期或格式不对，会抛出 JwtException，由调用方处理。
     */
    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }
}
