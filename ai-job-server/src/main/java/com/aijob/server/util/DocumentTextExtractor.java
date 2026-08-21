package com.aijob.server.util;

import java.nio.file.Path;

/**
 * 按 fileType 分发的统一文本抽取门面。
 */
public final class DocumentTextExtractor {

    private DocumentTextExtractor() {
    }

    public static String extract(Path filePath, String fileType) {
        return extractDetailed(filePath, fileType).text();
    }

    public static DocumentExtractResult extractDetailed(Path filePath, String fileType) {
        String type = fileType == null ? "" : fileType.trim().toUpperCase();
        return switch (type) {
            case "PDF" -> {
                PdfTextExtractor.PdfExtractResult pdf = PdfTextExtractor.extractDetailed(filePath);
                yield new DocumentExtractResult(pdf.text(), pdf.pageCount());
            }
            case "MD", "MARKDOWN" -> MdTextExtractor.extractDetailed(filePath);
            default -> throw new RuntimeException("不支持的知识库文件类型：" + fileType);
        };
    }
}
