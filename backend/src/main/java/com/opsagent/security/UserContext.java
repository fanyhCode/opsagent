package com.opsagent.security;

/**
 * 当前请求的用户上下文（基于 ThreadLocal）。
 *
 * 背景知识：Tomcat 处理每个请求时，会从线程池里拿一个线程。
 * ThreadLocal 让每个线程拥有自己独立的一份数据，因此不同请求之间不会互相干扰，
 * 这样业务代码在任意地方都能通过 UserContext.get() 拿到当前登录用户，
 * 不用把用户信息一层层当参数往下传。
 *
 * 重要：请求结束后必须调用 clear()。因为线程会被放回线程池重复使用，
 * 如果不清除，下一个请求可能读到上一个用户的数据（严重的安全问题）。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        CURRENT_USER.set(user);
    }

    public static LoginUser get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
