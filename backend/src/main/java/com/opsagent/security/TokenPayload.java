package com.opsagent.security;

/**
 * 从 JWT 中解析出来的内容。
 *
 * @param user         用户信息（id、用户名、角色）
 * @param sessionStart 本次会话的起始时间（毫秒时间戳）。
 *                     滑动续期时会把它原样继承下去，用来计算"这次登录已经持续了多久"，
 *                     从而实现"令牌可以自动续，但会话不能无限长"的绝对上限。
 * @param expiresAt    当前令牌的过期时间（毫秒时间戳）
 */
public record TokenPayload(LoginUser user, long sessionStart, long expiresAt) {
}
