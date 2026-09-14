package com.opsagent.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * @RestControllerAdvice 的作用：拦截所有 Controller 抛出的异常，统一在这里转换成 Result 返回，
 * 业务代码里就不用写一堆 try/catch，代码会干净很多。
 *
 * 注意异常处理的"内外有别"：
 * - 业务异常：把具体原因告诉调用方（用户名已存在）；
 * - 系统异常：只记录详细日志给开发看，对外统一返回"系统繁忙"，不暴露内部实现细节。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：属于"预期内"的错误，HTTP 仍返回 200，用 code 区分 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数不合法（例如 Spring 解析请求体失败）也归到业务错误里 */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常：{}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    /** 其它所有异常：系统级错误，对外只说"系统繁忙"，细节写进日志 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
