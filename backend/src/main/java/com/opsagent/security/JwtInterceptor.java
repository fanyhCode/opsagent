package com.opsagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsagent.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 校验拦截器（含滑动续期）。
 *
 * 执行时机：请求进入 Controller 之前（preHandle）。处理顺序：
 * 1. 从请求头 Authorization 取出令牌（格式 Bearer <token>）；
 * 2. 验签 + 校验有效期，解析出用户与会话信息；
 * 3. 如果会话已超过最大时长 → 401，要求重新登录；
 * 4. 如果令牌快过期 → 顺手续一张新令牌，放进响应头 X-New-Token；
 * 5. 把用户放进 UserContext 后放行。
 *
 * 为什么用响应头下发新令牌？
 * 因为令牌续期是"顺带发生"的，不应该改变接口本身的返回结构；
 * 放在响应头里，前端拦截器统一读取即可，业务代码完全无感。
 */
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);

    private static final String BEARER_PREFIX = "Bearer ";

    /** 续期令牌的响应头名称 */
    public static final String HEADER_NEW_TOKEN = "X-New-Token";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtInterceptor(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 跨域预检请求不带令牌，直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "未登录，请先登录");
            return false;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            TokenPayload payload = jwtService.parse(token);

            // 会话超过绝对上限，即使令牌本身还没过期，也必须重新登录
            if (jwtService.isSessionExpired(payload.sessionStart())) {
                writeUnauthorized(response, "会话已到期，请重新登录");
                return false;
            }

            UserContext.set(payload.user());

            // 快过期就顺手续期，用户无感知
            if (jwtService.shouldRenew(payload.expiresAt())) {
                String newToken = jwtService.renew(payload);
                response.setHeader(HEADER_NEW_TOKEN, newToken);
                // 如果将来前后端分离部署（跨域），必须显式暴露这个响应头，
                // 否则浏览器端读不到它。现在走 Vite 代理是同源，加上也不影响。
                response.setHeader("Access-Control-Expose-Headers", HEADER_NEW_TOKEN);
                log.info("令牌已自动续期：user={}", payload.user().username());
            }

            return true;

        } catch (Exception e) {
            // 令牌过期、被篡改、格式错误都会走到这里
            log.warn("令牌校验失败：{}", e.getMessage());
            writeUnauthorized(response, "登录已过期或令牌无效，请重新登录");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束一定要清理 ThreadLocal，避免线程复用造成用户信息串台
        UserContext.clear();
    }

    /** 统一输出 401 的 JSON 结构，前端就能按同一套逻辑处理 */
    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(401, message)));
    }
}
