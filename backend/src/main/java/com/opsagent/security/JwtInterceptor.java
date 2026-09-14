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
 * JWT 校验拦截器。
 *
 * 执行时机：请求进入 Controller 之前（preHandle）。
 * 校验逻辑：
 * 1. 从请求头 Authorization 里取出令牌，格式约定为 "Bearer <token>"；
 * 2. 校验签名与有效期，解析出用户信息；
 * 3. 校验通过 → 把用户放进 UserContext，放行；
 * 4. 校验失败 → 直接返回 401，不再进入业务代码。
 */
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);

    /** 约定的请求头前缀，标准写法是 "Bearer " 后面跟令牌 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtInterceptor(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 浏览器跨域时的预检请求（OPTIONS）不带令牌，直接放行
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
            LoginUser user = jwtService.parseToken(token);
            UserContext.set(user);
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
