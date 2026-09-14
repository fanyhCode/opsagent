package com.opsagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsagent.security.JwtInterceptor;
import com.opsagent.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 拦截器并指定它拦截哪些接口。
 *
 * 默认策略是"拦截 /api/** 下的全部接口，只放行白名单"，
 * 这叫"默认拒绝"，比"默认放行 + 逐个加保护"安全得多——
 * 新加接口时忘了加保护也不会裸奔。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public WebMvcConfig(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtInterceptor(jwtService, objectMapper))
                .addPathPatterns("/api/**")
                // 白名单：登录、注册、健康检查不需要令牌
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/health");
    }
}
