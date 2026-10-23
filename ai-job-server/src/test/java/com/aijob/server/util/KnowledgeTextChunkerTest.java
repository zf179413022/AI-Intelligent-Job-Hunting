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
    void chunkCarriesThreeLevelHeadingPath() {
        List<DocumentBlock> blocks = List.of(
                DocumentBlock.heading(1, "Redis"),
                DocumentBlock.heading(2, "解决方案"),
                DocumentBlock.heading(3, "布隆过滤器"),
                DocumentBlock.paragraph("可以快速判断一个元素是否可能存在")
        );

        List<String> chunks = KnowledgeTextChunker.chunkBlocks(blocks, 300, 20);

        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().startsWith("Redis > 解决方案 > 布隆过滤器"));
    }

    @Test
    void longCodeIsSplitAtLogicalBoundaries() {
        String code = String.join("\n",
                "public class Demo {",
                "",
                "    public void methodA() {",
                "        int a = 1;",
                "        int b = 2;",
                "        int c = 3;",
                "        int d = 4;",
                "        int e = 5;",
                "        System.out.println(a + b + c + d + e);",
                "    }",
                "",
                "    public void methodB() {",
                "        String x = \"x\";",
                "        String y = \"y\";",
                "        String z = \"z\";",
                "        System.out.println(x + y + z);",
                "    }",
                "}");

        List<String> chunks = KnowledgeTextChunker.chunkBlocks(
                List.of(DocumentBlock.code(code, "java")), 160, 10);

        assertTrue(chunks.size() >= 2, "超长代码应切片：" + chunks);
        for (String chunk : chunks) {
            assertTrue(chunk.startsWith("```java"), chunk);
            assertTrue(chunk.endsWith("```"), chunk);
        }
        assertTrue(chunks.stream().anyMatch(c -> c.contains("methodA") && !c.contains("methodB")),
                "方法 A 不应与 B 混在同一片：" + chunks);
        assertTrue(chunks.stream().anyMatch(c -> c.contains("methodB")),
                "方法 B 应完整出现在某一片：" + chunks);
    }

    @Test
    void tokenEstimateCountsCjkDenserThanLatin() {
        assertEquals(10, TokenEstimator.estimate("这是一段中文文本内容"));
        int latin = TokenEstimator.estimate("abcdefghijklmnopqrstuvwxyz");
        assertTrue(latin < 10, "26 个英文字母应远少于 10 token，实际 " + latin);
    }

    @Test
    void shortIntroIsMergedIntoAtomicChunk() {
        List<DocumentBlock> blocks = List.of(
                DocumentBlock.heading(2, "对比表"),
                DocumentBlock.paragraph("下面是结构对比："),
                DocumentBlock.table("| a | b |\n| --- | --- |\n| 1 | 2 |")
        );

        List<String> chunks = KnowledgeTextChunker.chunkBlocks(blocks, 500, 20);

        assertEquals(1, chunks.size(), "短引言应并入表格 chunk：" + chunks);
        assertTrue(chunks.getFirst().contains("下面是结构对比："));
        assertTrue(chunks.getFirst().contains("| --- | --- |"));
    }

    /** 与 samples/code-table-demo.md 对齐的验收文档：锁住「解析 → 切分」链路的内容保真。 */
    @Test
    void acceptanceDocumentKeepsCodeAndTableIntact() {
        String md = """
                # HashMap 与 ConcurrentHashMap

                这段文档用来验收知识库解析：表格应保持 Markdown 表格，代码块应保留围栏和正文。

                ## 对比表

                | 结构 | 线程安全 | 适用场景 |
                | --- | --- | --- |
                | HashMap | 否 | 单线程缓存 |
                | ConcurrentHashMap | 是 | 高并发读写 |
                | Hashtable | 是，整表锁 | 遗留代码 |

                单元格里的竖线要保留，不能被拆成新列：`a \\| b`，行内代码 `put(k\\|v)`。

                ## 示例代码

                下面这段不是标题，链接也不该被改写。

                ```java
                # 这行是注释，不是标题
                public class Demo {
                    public static void main(String[] args) {
                        String url = "[文档](https://example.com)";
                        System.out.println(url);
                    }
                }
                ```
                """;

        List<String> chunks = KnowledgeTextChunker.chunkBlocks(MarkdownDocumentParser.parse(md), 700, 100);

        String codeChunk = chunks.stream()
                .filter(chunk -> chunk.contains("```java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少代码块 chunk：" + chunks));
        assertTrue(codeChunk.contains("String url = \"[文档](https://example.com)\";"),
                "代码块内的链接必须原样保留：" + codeChunk);
        assertTrue(codeChunk.contains("# 这行是注释，不是标题"), "代码注释不能被当成标题：" + codeChunk);
        assertTrue(codeChunk.stripTrailing().endsWith("```"), "代码围栏必须闭合：" + codeChunk);
        assertFalse(codeChunk.contains("\\("), "不应出现括号转义：" + codeChunk);
        assertFalse(codeChunk.contains("&#xA;"), "不应出现 HTML 实体换行：" + codeChunk);
        assertFalse(codeChunk.contains("\\\\"), "不应出现双反斜杠：" + codeChunk);

        String tableChunk = chunks.stream()
                .filter(chunk -> chunk.contains("| HashMap |"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少表格 chunk：" + chunks));
        assertTrue(tableChunk.contains("\n| --- | --- | --- |\n"), "表格必须保留分隔行与换行：" + tableChunk);

        String noteChunk = chunks.stream()
                .filter(chunk -> chunk.contains("单元格里的竖线"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少竖线说明 chunk：" + chunks));
        assertTrue(noteChunk.contains("`a \\| b`"), "竖线转义必须保留：" + noteChunk);
        assertFalse(noteChunk.contains("\\\\|"), "竖线不能被重复转义：" + noteChunk);
    }

    @Test
    void plainTextChunkingStillWorks() {
        List<String> chunks = KnowledgeTextChunker.chunk("第一段。\n\n第二段。", 100, 10);

        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().contains("第一段。"));
        assertFalse(chunks.getFirst().isBlank());
    }
}
