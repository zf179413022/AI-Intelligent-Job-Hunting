package com.aijob.server.util;

/**
 * 文档解析异常：message 已包含可定位的信息（文件类型 / 页码 / 行号 / 编码位置）。
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
