package com.aijob.server.util;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdTextExtractorTest {

    @Test
    void clean_keepsHeadingText_andCodeBody_stripsFenceAndUrls() {
        String raw = """
                ---
                title: demo
                ---
                # Java 集合
                ## HashMap
                HashMap 在 JDK 8 中采用数组 + 链表 + 红黑树。
                ![HashMap结构图](https://example.com/a.png)
                ![](https://example.com/empty.png)
                详见 [HashMap源码](https://github.com/example)。
                ```java
                Map<String, Object> map = new HashMap<>();
                ```
                """;

        String cleaned = MdTextExtractor.clean(raw);

        assertTrue(cleaned.contains("Java 集合"));
        assertTrue(cleaned.contains("HashMap"));
        assertTrue(cleaned.contains("数组 + 链表 + 红黑树"));
        assertTrue(cleaned.contains("HashMap结构图"));
        assertTrue(cleaned.contains("HashMap源码"));
        assertTrue(cleaned.contains("Map<String, Object> map = new HashMap<>();"));
        assertFalse(cleaned.contains("```"));
        assertFalse(cleaned.contains("https://"));
        assertFalse(cleaned.matches("(?s).*\\bjava\\nMap.*"));
        // 语言标记不应作为独立行污染
        assertFalse(cleaned.contains("\njava\n"));
        assertFalse(cleaned.startsWith("java\n"));
    }

    @Test
    void extractDetailed_pageCountIsNull() throws Exception {
        Path tmp = Files.createTempFile("md-extract-", ".md");
        try {
            Files.writeString(tmp, "# Title\nhello\n", StandardCharsets.UTF_8);
            DocumentExtractResult result = MdTextExtractor.extractDetailed(tmp);
            assertEquals("Title\nhello", result.text());
            assertNull(result.pageCount());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void extract_rejectsInvalidUtf8() throws Exception {
        Path tmp = Files.createTempFile("md-bad-", ".md");
        try {
            Files.write(tmp, new byte[]{(byte) 0xC3, (byte) 0x28});
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> MdTextExtractor.extract(tmp));
            assertTrue(ex.getMessage().contains("UTF-8"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void facade_routesByFileType() throws Exception {
        Path tmp = Files.createTempFile("md-facade-", ".md");
        try {
            Files.writeString(tmp, "## A\nbody\n", StandardCharsets.UTF_8);
            DocumentExtractResult result = DocumentTextExtractor.extractDetailed(tmp, "MD");
            assertEquals("A\nbody", result.text());
            assertNull(result.pageCount());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void utf8Strict_rejectsMalformed() {
        byte[] bad = new byte[]{(byte) 0xFF};
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        assertThrows(CharacterCodingException.class,
                () -> decoder.decode(ByteBuffer.wrap(bad)));
    }
}
