package com.aetherlearn.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器（F-AUTH / 基础支撑）
 * <p>统一拦截业务异常、权限异常、未认证异常等，返回统一 {@link Result} 结构。</p>
 * <p>安全策略：对外仅暴露友好提示，异常详情通过日志记录（服务端排查用）。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常（如账号/密码错误） */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 未认证（JWT 缺失/非法/过期） → 401 */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuth(AuthenticationException e) {
        return Result.error(401, "未认证或登录已过期");
    }

    /** 无权限（角色不匹配） → 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccess(AccessDeniedException e) {
        return Result.error(403, "权限不足，无法访问该资源");
    }

    /** 其余未捕获异常 — 对外隐藏内部信息，详情写日志 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e, HttpServletRequest request) {
        log.error("未捕获异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error(500, "服务器内部错误，请稍后重试");
    }
}
