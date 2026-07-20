package com.aetherlearn.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结构（F-AUTH / 基础支撑）
 * <p>所有 Controller 接口返回统一包装：{ code, message, data }。</p>
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    /** 业务状态码：200 成功，其余为失败 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /** 成功（自定义提示） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 失败（带状态码与提示） */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败（默认 500） */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
