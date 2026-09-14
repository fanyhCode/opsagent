package com.opsagent.dto;

/**
 * 注册请求参数。
 *
 * 使用 record（Java 16+ 的新语法）：一行就等价于一个包含 final 字段、
 * 全参构造方法、getter（这里叫 username()、password()）的不可变类，
 * 非常适合用来承载接口的请求/响应数据。
 *
 * 前端传来的 JSON {"username":"admin","password":"123456","nickname":"管理员"}
 * 会被 Spring 自动映射成这个对象。
 */
public record RegisterRequest(String username, String password, String nickname) {
}
