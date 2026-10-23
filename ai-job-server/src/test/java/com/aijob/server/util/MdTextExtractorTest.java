package com.aijob.server.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdTextExtractorTest {

    @Test
    void clean_keepsStructure_dropsUrlsAndFrontmatter() {
        String raw = """
                ---
                title: demo
                ---
                # Java 集合
                ## HashMap
                HashMap 在 JDK 8 中采用数组 + 链表 + 红黑树。
                ![HashMap结构图](https://example.com/a.png)
                详见 [HashMap源码](https://github.com/example)。
                | 结构 | 复杂度 |
                | --- | --- |
                | 链表 | O(n) |
                ```java
                Map<String, Object> map = new HashMap<>();
                ```
                """;

        String cleaned = MdTextExtractor.clean(raw);

        assertTrue(cleaned.contains("# Java 集合"));
        assertTrue(cleaned.contains("## HashMap"));
        assertTrue(cleaned.contains("数组 + 链表 + 红黑树"));
        assertTrue(cleaned.contains("HashMap结构图"));
        assertTrue(cleaned.contains("HashMap源码"));
        assertTrue(cleaned.contains("| 链表 | O(n) |"), "表格结构必须保留");
        assertTrue(cleaned.contains("```java"), "代码围栏与语言必须保留");
        assertTrue(cleaned.contains("Map<String, Object> map = new HashMap<>();"));
        assertFalse(cleaned.contains("https://"));
        assertFalse(cleaned.contains("title: demo"));
    }

    @Test
    void clean_doesNotTreatCodeCommentAsHeading() {
        String raw = """
                ```python
                # 这是注释
                x = 1
                ```
                """;

        String cleaned = MdTextExtractor.clean(raw);

        assertTrue(cleaned.contains("# 这是注释"));
    }

    @Test
    void extractDetailed_pageCountIsNull() throws Exception {
        Path tmp = Files.createTempFile("md-extract-", ".md");
        try {
            Files.writeString(tmp, "# Title\nhello\n");
            DocumentExtractResult result = MdTextExtractor.extractDetailed(tmp);
            assertEquals("# Title\n\nhello", result.text());
            assertNull(result.pageCount());
            assertEquals(2, result.blocks().size());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void extract_rejectsInvalidUtf8WithLineNumber() throws Exception {
        Path tmp = Files.createTempFile("md-bad-", ".md");
        try {
            Files.write(tmp, "第一行\n第二行\n".getBytes());
            Files.write(tmp, new byte[]{(byte) 0xC3, (byte) 0x28}, StandardOpenOption.APPEND);
            DocumentParseException ex = assertThrows(DocumentParseException.class,
                    () -> MdTextExtractor.extract(tmp));
            assertTrue(ex.getMessage().contains("UTF-8"));
            assertTrue(ex.getMessage().contains("第 3 行"), ex.getMessage());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void facade_routesByFileType() throws Exception {
        Path tmp = Files.createTempFile("md-facade-", ".md");
        try {
            Files.writeString(tmp, "## A\nbody\n");
            DocumentExtractResult result = DocumentTextExtractor.extractDetailed(tmp, "MD");
            assertEquals("## A\n\nbody", result.text());
            assertNull(result.pageCount());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void facade_rejectsUnsupportedType() {
        DocumentParseException ex = assertThrows(DocumentParseException.class,
                () -> DocumentTextExtractor.extractDetailed(Path.of("x.docx"), "DOCX"));
        assertTrue(ex.getMessage().contains("DOCX"));
    }
}
