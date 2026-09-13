package com.aijob.server.exception;

/**
 * 资源归属校验失败：当前用户无权访问目标资源。
 * 由全局异常处理映射为 HTTP 403。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
