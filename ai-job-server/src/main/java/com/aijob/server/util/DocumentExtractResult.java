package com.aijob.server.util;

/**
 * 统一文档文本抽取结果。PDF 有 pageCount；MD 的 pageCount 恒为 null。
 */
public record DocumentExtractResult(String text, Integer pageCount) {
}
