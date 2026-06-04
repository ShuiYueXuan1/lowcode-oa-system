package com.oa.lowcode.config;

import com.oa.lowcode.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * <p>统一拦截 Controller 层抛出的异常，返回 Result 格式的错误响应。
 * <ul>
 *   <li>IllegalArgumentException → 直接返回 message（业务校验类错误）</li>
 *   <li>其他 Exception → 记录日志后返回 message（系统异常）</li>
 * </ul></p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 系统异常：记录完整堆栈，返回友好提示 */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(e.getMessage());
    }

    /** 业务校验异常：直接返回 message */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgument(IllegalArgumentException e) {
        return Result.fail(e.getMessage());
    }
}
