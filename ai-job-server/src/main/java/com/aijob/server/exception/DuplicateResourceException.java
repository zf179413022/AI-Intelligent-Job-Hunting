package com.aijob.server.exception;

/**
 * 资源重复（如相同内容文件重复上传）。由全局异常处理映射为 HTTP 409。
 */
public class DuplicateResourceException extends RuntimeException {

    private final Long existingDocumentId;

    public DuplicateResourceException(String message, Long existingDocumentId) {
        super(message);
        this.existingDocumentId = existingDocumentId;
    }

    public Long getExistingDocumentId() {
        return existingDocumentId;
    }
}
