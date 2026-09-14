package com.opsagent.common;

/**
 * 统一的接口返回结构。
 *
 * 为什么需要统一？如果每个接口各返回各的（有的返回 List、有的返回 Map、有的直接返回字符串），
 * 前端就得为每个接口写不同的解析逻辑。统一成 {code, message, data} 之后：
 * 1. 前端只写一套处理逻辑，先看 code，再取 data；
 * 2. 出错时也有固定的结构，方便统一弹提示；
 * 3. 接口文档写起来简单，面试时也体现工程规范意识。
 *
 * code 约定：200 成功；400 业务参数/规则错误；401 未登录或令牌失效；500 系统异常。
 */
public class Result<T> {

    /** 业务状态码 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 业务数据，出错时为 null */
    private T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功，带数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /** 成功，自定义提示 */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 失败，只给提示（默认业务错误码 400） */
    public static <T> Result<T> fail(String message) {
        return new Result<>(400, message, null);
    }

    /** 失败，指定状态码 */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
