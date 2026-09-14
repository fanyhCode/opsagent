package com.opsagent.controller;

import com.opsagent.common.BizException;
import com.opsagent.common.Result;
import com.opsagent.dto.LoginRequest;
import com.opsagent.dto.RegisterRequest;
import com.opsagent.entity.SysUser;
import com.opsagent.security.JwtService;
import com.opsagent.security.LoginUser;
import com.opsagent.security.UserContext;
import com.opsagent.service.SysUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口：注册、登录、获取当前登录用户。
 *
 * /api/auth/register 和 /api/auth/login 在拦截器白名单里，不需要令牌；
 * 其它接口（包括 /api/auth/me）都必须携带令牌。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserService sysUserService;
    private final JwtService jwtService;

    public AuthController(SysUserService sysUserService, JwtService jwtService) {
        this.sysUserService = sysUserService;
        this.jwtService = jwtService;
    }

    /**
     * POST /api/auth/register
     * 请求体示例：{"username":"admin","password":"admin123","nickname":"管理员"}
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        SysUser user = sysUserService.register(
                request.username(), request.password(), request.nickname());
        return Result.ok("注册成功", toSafeMap(user));
    }

    /**
     * POST /api/auth/login
     * 请求体示例：{"username":"admin","password":"admin123"}
     *
     * 登录成功后返回令牌，前端要把它存起来（通常放 localStorage），
     * 之后每次请求都在请求头里带上：Authorization: Bearer <token>
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        SysUser user = sysUserService.login(request.username(), request.password());

        // 签发令牌：JWT 里放用户 id、用户名、角色，不放密码
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> data = toSafeMap(user);
        data.put("token", token);
        return Result.ok("登录成功", data);
    }

    /**
     * GET /api/auth/me
     * 返回当前登录用户。这个接口用来演示"受保护的接口"：
     * 不带令牌会返回 401，带有效令牌才返回用户信息。
     */
    @GetMapping("/me")
    public Result<LoginUser> me() {
        LoginUser currentUser = UserContext.get();
        if (currentUser == null) {
            throw new BizException(401, "未登录");
        }
        return Result.ok(currentUser);
    }

    /**
     * 把用户对象转成可以安全返回给前端的结构。
     * 刻意不包含 password 字段——即使数据库里存的是哈希，也没必要返回给客户端。
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
