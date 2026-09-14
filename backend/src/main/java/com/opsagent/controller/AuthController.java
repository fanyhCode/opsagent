package com.opsagent.controller;

import com.opsagent.dto.LoginRequest;
import com.opsagent.dto.RegisterRequest;
import com.opsagent.entity.SysUser;
import com.opsagent.service.SysUserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口：注册与登录。
 *
 * 这一版的返回值先用简单的 Map 表示成功/失败，
 * 下一步会引入统一的响应结构和 JWT，届时会统一改造。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserService sysUserService;

    public AuthController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * POST /api/auth/register
     * 请求体示例：{"username":"admin","password":"admin123","nickname":"管理员"}
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        try {
            SysUser user = sysUserService.register(
                    request.username(), request.password(), request.nickname());
            return success("注册成功", toSafeMap(user));
        } catch (IllegalArgumentException e) {
            // 业务校验失败属于"可预期的错误"，返回失败原因而不是 500 异常
            return fail(e.getMessage());
        }
    }

    /**
     * POST /api/auth/login
     * 请求体示例：{"username":"admin","password":"admin123"}
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        try {
            SysUser user = sysUserService.login(request.username(), request.password());
            return success("登录成功", toSafeMap(user));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    private Map<String, Object> success(String message, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }

    /**
     * 把用户对象转成可以安全返回给前端的结构。
     * 刻意不包含 password 字段——即使数据库里存的是哈希，也没有必要返回给客户端。
     */
    private Map<String, Object> toSafeMap(SysUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        return data;
    }
}
