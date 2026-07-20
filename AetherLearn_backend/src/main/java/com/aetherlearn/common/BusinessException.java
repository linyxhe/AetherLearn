package com.aetherlearn.common;

import lombok.Getter;

/**
 * 业务异常（F-AUTH / 基础支撑）
 * <p>用于 Service 层抛出可预期的业务错误（如账号不存在、密码错误）。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码 */
    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
