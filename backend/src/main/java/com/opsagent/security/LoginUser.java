package com.opsagent.security;

/**
 * 当前登录用户的信息，从 JWT 令牌里解析出来后放在请求上下文中。
 * 使用 record 表示这是一个不可变的数据载体。
 */
public record LoginUser(Long id, String username, String role) {
}
