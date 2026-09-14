package com.opsagent.common;

/**
 * 业务异常。
 *
 * 用法：业务规则不满足时抛出它，例如"用户名已存在"。
 * 它会被 GlobalExceptionHandler 捕获并转成统一的返回结构，
 * 这样业务代码里就不用到处写 try/catch 和拼装返回值。
 *
 * 与系统异常的区别：
 * - 业务异常是"可预期的"，比如参数不对、状态不合法，提示可以直接给用户看；
 * - 系统异常是"意外的"，比如空指针、数据库连不上，不能把原始堆栈泄露给用户。
 */
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
