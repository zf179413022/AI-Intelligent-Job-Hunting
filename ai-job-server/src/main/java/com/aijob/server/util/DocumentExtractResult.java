package com.aijob.server.util;

import java.util.List;

/**
 * 统一文档抽取结果：结构化块 + 渲染文本 + 页数 + 非致命告警。
 * <p>
 * blocks 供分块器保留表格/代码边界；text 供需要纯文本的调用方直接使用。
 */
public record DocumentExtractResult(String text, Integer pageCount,
                                    List<DocumentBlock> blocks, List<String> warnings) {

    public DocumentExtractResult {
        text = text == null ? "" : text;
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public DocumentExtractResult(String text, Integer pageCount) {
        this(text, pageCount, List.of(), List.of());
    }

    public boolean isEmpty() {
        return text.isBlank();
    }
}
