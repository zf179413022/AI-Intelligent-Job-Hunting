package com.aijob.server.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeTextChunkerTest {

    @Test
    void tableStaysInsideOneChunk() {
        List<DocumentBlock> blocks = List.of(
                DocumentBlock.heading(1, "Java"),
                DocumentBlock.paragraph("表格前的说明"),
                DocumentBlock.table("| a | b |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |")
        );

        List<String> chunks = KnowledgeTextChunker.chunkBlocks(blocks, 200, 20);

        assertTrue(chunks.stream().anyMatch(chunk -> chunk.contains("| 3 | 4 |")));
        // 表格靠换行分列，压成一行会让 LLM 无法区分「行」与「列」
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.contains("\n| --- | --- |\n")),
                "表格 chunk 必须保留换行：" + chunks);
    }

    @Test
    void codeChunkKeepsIndentAndLineBreaks() {
        String code = "public class Demo {\n    void run() {\n        System.out.println(1);\n    }\n}";
        List<DocumentBlock> blocks = List.of(
                DocumentBlock.heading(2, "示例代码"),
                DocumentBlock.code(code, "java")
        );

        List<String> chunks = KnowledgeTextChunker.chunkBlocks(blocks, 300, 20);

        assertTrue(chunks.stream().anyMatch(chunk -> chunk.contains("```java\n" + code + "\n```")),
                "代码 chunk 必须保留围栏、缩进与换行：" + chunks);
    }

    @Test
    void chunkCarriesHeadingPathPrefix() {
        List<DocumentBlock> blocks = List.of(
                DocumentBlock.heading(1, "Java"),
                DocumentBlock.heading(2, "HashMap"),
                DocumentBlock.paragraph("底层是数组 + 链表 + 红黑树")
        );

        List<String> chunks = KnowledgeTextChunker.chunkBlocks(blocks, 200, 20);

        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().startsWith("Java > HashMap"));
    }

    @Test
    void longTableRepeatsHeaderInEverySlice() {
        List<String> rows = new ArrayList<>();
        rows.add("| 编号 | 说明 |");
        rows.add("| --- | --- |");
        for (int i = 0; i < 80; i++) {
            rows.add("| " + i + " | 说明文字" + i + " |");
        }
        List<DocumentBlock> blocks = List.of(DocumentBlock.table(String.join("\n", rows)));

        List<String> chunks = KnowledgeTextChunker.chunkBlocks(blocks, 300, 20);

        assertTrue(chunks.size() > 1, "超长表格应切片");
        for (String chunk : chunks) {
            assertTrue(chunk.startsWith("| 编号 | 说明 |"), "每片都要带表头：" + chunk);
            assertTrue(chunk.contains("| --- | --- |"), "每片都要带分隔行：" + chunk);
        }
    }

    @Test
    void codeBlockIsNotSplitWhenWithinBudget() {
        String code = "public class A {\n    void run() {\n        System.out.println(1);\n    }\n}";
        List<DocumentBlock> blocks = List.of(
                DocumentBlock.paragraph("示例代码"),
                DocumentBlock.code(code, "java")
        );

        List<String> chunks = KnowledgeTextChunker.chunkBlocks(blocks, 500, 20);

        assertTrue(chunks.stream().anyMatch(chunk -> chunk.contains(code)), "代码块不得被拆开");
    }

    @Test
    void plainTextChunkingStillWorks() {
        List<String> chunks = KnowledgeTextChunker.chunk("第一段。\n\n第二段。", 100, 10);

        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().contains("第一段。"));
        assertFalse(chunks.getFirst().isBlank());
    }
}
