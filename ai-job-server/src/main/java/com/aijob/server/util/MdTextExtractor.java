package com.aijob.server.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Markdown → 结构化 Markdown 文本（面向 RAG Embedding 与 LLM 阅读）。
 * <p>
 * 保留：标题层级、表格、代码围栏与语言、列表、脚注定义。<br>
 * 清理：YAML frontmatter、链接/图片 URL、HTML 标签、强调标记、反斜杠转义。
 * <p>
 * 解析逻辑见 {@link MarkdownDocumentParser}，本类只负责编码校验与门面。
 */
public final class MdTextExtractor {

    private MdTextExtractor() {
    }

    public static String extract(Path filePath) {
        return extractDetailed(filePath).text();
    }

    public static DocumentExtractResult extractDetailed(Path filePath) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new DocumentParseException("Markdown 文件读取失败：" + e.getMessage(), e);
        }
        String raw = decodeUtf8(bytes);
        List<DocumentBlock> blocks = MarkdownDocumentParser.parse(raw);
        return new DocumentExtractResult(
                DocumentTextRenderer.render(blocks), null, blocks, List.of());
    }

    static String clean(String raw) {
        return DocumentTextRenderer.render(MarkdownDocumentParser.parse(raw));
    }

    /** 严格 UTF-8 解码，失败时定位到行号，便于用户直接修复源文件。 */
    private static String decodeUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new DocumentParseException(
                    "Markdown 不是合法 UTF-8 编码（第 " + firstInvalidLine(bytes) + " 行附近）", e);
        }
    }

    /** 按 UTF-8 规则扫描字节，返回第一个非法序列所在行号（1 起）。 */
    private static int firstInvalidLine(byte[] bytes) {
        int line = 1;
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            if (b == '\n') {
                line++;
                i++;
                continue;
            }
            if (b < 0x80) {
                i++;
                continue;
            }
            int need;
            if ((b & 0xE0) == 0xC0 && b >= 0xC2) {
                need = 1;
            } else if ((b & 0xF0) == 0xE0) {
                need = 2;
            } else if ((b & 0xF8) == 0xF0 && b <= 0xF4) {
                need = 3;
            } else {
                return line;
            }
            if (i + need >= bytes.length) {
                return line;
            }
            for (int k = 1; k <= need; k++) {
                if ((bytes[i + k] & 0xC0) != 0x80) {
                    return line;
                }
            }
            i += need + 1;
        }
        return line;
    }
}
