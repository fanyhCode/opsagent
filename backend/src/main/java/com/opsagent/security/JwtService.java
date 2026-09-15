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
 * JWT 令牌服务：负责"签发令牌""解析令牌"和"滑动续期"。
 *
 * JWT 长什么样？
 *   头部.载荷.签名   （三部分用点分隔）
 * 其中：
 * - 载荷是明文，任何人都能解开看，所以绝不能放密码等敏感信息；
 * - 签名用密钥对前两段做摘要，改一个字符就对不上，因此令牌无法伪造；
 * - 服务器不需要保存会话，每次验签即可确认身份，这就是"无状态认证"。
 *
 * 关于滑动续期（本类的重点）：
 * 令牌有效期只有 30 分钟，但如果用户一直在操作，我们希望他感觉不到过期。
 * 做法是：每次请求校验通过后，如果发现令牌快过期了，就顺手续一张新的。
 * 但"续"不能无限进行，否则令牌等于永久有效，所以令牌里额外记录一个
 * sessionStart（最初登录时间），续签时原样继承，用它来卡"会话最大 12 小时"的上限。
 */
@Service
public class JwtService {

    /** 自定义声明：本次会话的起始时间。注意它不是标准的 JWT 字段，是我们自己加的。 */
    private static final String CLAIM_SESSION_START = "sessionStart";

    private final SecretKey secretKey;

    /** 单个令牌的有效期（分钟） */
    private final long expireMinutes;

    /** 续签阈值（分钟）：剩余有效期少于它就该续签了 */
    private final long renewThresholdMinutes;

    /** 会话最大时长（小时）：超过就强制重新登录 */
    private final long maxSessionHours;

    public JwtService(@Value("${opsagent.jwt.secret}") String secret,
                      @Value("${opsagent.jwt.expire-minutes}") long expireMinutes,
                      @Value("${opsagent.jwt.renew-threshold-minutes}") long renewThresholdMinutes,
                      @Value("${opsagent.jwt.max-session-hours}") long maxSessionHours) {
        // HS256/HS384 要求密钥至少 256 位，Keys.hmacShaKeyFor 会做长度校验
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
        this.renewThresholdMinutes = renewThresholdMinutes;
        this.maxSessionHours = maxSessionHours;
    }

    /** 登录成功时签发令牌：会话起始时间就是现在 */
    public String generateToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, System.currentTimeMillis());
    }

    /**
     * 续签令牌：用户信息和"会话起始时间"原样继承，只把过期时间往后推。
     * 关键点：一定要继承 sessionStart，否则每续一次都把计时清零，12 小时上限就永远到不了。
     */
    public String renew(TokenPayload payload) {
        return buildToken(payload.user().id(), payload.user().username(),
                payload.user().role(), payload.sessionStart());
    }

    private String buildToken(Long userId, String username, String role, long sessionStart) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMinutes * 60 * 1000);

        return Jwts.builder()
                .subject(String.valueOf(userId))          // sub：用户 id
                .claim("username", username)
                .claim("role", role)
                .claim(CLAIM_SESSION_START, sessionStart) // 会话起始时间
                .issuedAt(now)                            // iat：签发时间
                .expiration(expiration)                   // exp：过期时间
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌。令牌被篡改、已过期或格式不对都会抛出 JwtException。
     */
    public TokenPayload parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        LoginUser user = new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class));

        // 兼容旧令牌：早期签发的令牌没有 sessionStart 字段，就用签发时间兜底
        Number sessionStartClaim = claims.get(CLAIM_SESSION_START, Number.class);
        long sessionStart = sessionStartClaim != null
                ? sessionStartClaim.longValue()
                : claims.getIssuedAt().getTime();

        return new TokenPayload(user, sessionStart, claims.getExpiration().getTime());
    }

    /** 令牌剩余有效期是否已经低于续签阈值 */
    public boolean shouldRenew(long expiresAt) {
        long remainingMinutes = (expiresAt - System.currentTimeMillis()) / 60000;
        return remainingMinutes < renewThresholdMinutes;
    }

    /** 会话是否已经超过最大时长（绝对上限） */
    public boolean isSessionExpired(long sessionStart) {
        long hours = (System.currentTimeMillis() - sessionStart) / 3600000;
        return hours >= maxSessionHours;
    }
}
