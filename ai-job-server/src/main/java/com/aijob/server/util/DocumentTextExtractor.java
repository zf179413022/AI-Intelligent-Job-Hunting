package com.aijob.server.util;

import java.nio.file.Path;
import java.util.List;

/**
 * 按 fileType 分发的统一文档抽取门面。
 * <p>
 * 所有格式都产出 {@link DocumentBlock} 序列，渲染文本与分块共用同一份结构，
 * 避免「解析一套、切分再猜一套」。
 */
public final class DocumentTextExtractor {

    private DocumentTextExtractor() {
    }

    public static String extract(Path filePath, String fileType) {
        return extractDetailed(filePath, fileType).text();
    }

    public static List<DocumentBlock> extractBlocks(Path filePath, String fileType) {
        return extractDetailed(filePath, fileType).blocks();
    }

    public static DocumentExtractResult extractDetailed(Path filePath, String fileType) {
        String type = fileType == null ? "" : fileType.trim().toUpperCase();
        return switch (type) {
            case "PDF" -> {
                PdfDocumentParser.ParseResult pdf = PdfDocumentParser.parse(filePath);
                yield new DocumentExtractResult(
                        DocumentTextRenderer.render(pdf.blocks()),
                        pdf.pageCount(),
                        pdf.blocks(),
                        pdf.warnings()
                );
            }
            case "MD", "MARKDOWN" -> MdTextExtractor.extractDetailed(filePath);
            default -> throw new DocumentParseException("不支持的知识库文件类型：" + fileType);
        };
    }
}
