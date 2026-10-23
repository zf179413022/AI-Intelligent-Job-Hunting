package com.aijob.server.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownDocumentParserTest {

    @Test
    void parsesGfmTableWithHeaderDelimiterAndRows() {
        String md = """
                | 语言 | 特点 |
                | --- | --- |
                | Java | 静态类型 |
                | Python | 动态类型 |
                """;

        List<DocumentBlock> blocks = MarkdownDocumentParser.parse(md);

        assertEquals(1, blocks.size());
        DocumentBlock table = blocks.getFirst();
        assertEquals(DocumentBlock.Type.TABLE, table.type());
        assertTrue(table.text().contains("| 语言 | 特点 |"));
        assertTrue(table.text().contains("| --- | --- |"));
        assertTrue(table.text().contains("| Java | 静态类型 |"));
        assertTrue(table.text().contains("| Python | 动态类型 |"));
    }

    @Test
    void tableKeepsEscapedPipeAndEscapesPipeInsideInlineCode() {
        String md = """
                | 表达式 | 说明 |
                | --- | --- |
                | a \\| b | `x|y` |
                """;

        DocumentBlock table = MarkdownDocumentParser.parse(md).getFirst();

        assertTrue(table.text().contains("a \\| b"));
        assertTrue(table.text().contains("`x\\|y`"));
        assertFalse(table.text().contains("| a | b |"), "未转义的竖线不得拆分单元格");
    }

    @Test
    void tablePadsMissingCellsToHeaderWidth() {
        String md = """
                | a | b | c |
                | --- | --- | --- |
                | 1 | 2 |
                """;

        DocumentBlock table = MarkdownDocumentParser.parse(md).getFirst();

        assertTrue(table.text().contains("| 1 | 2 |  |"), "缺列必须以空单元格补齐：" + table.text());
    }

    @Test
    void codeBlockBodyIsNeverRewritten() {
        String md = """
                ```java
                # 这行是注释，不是标题
                String url = "[文档](https://example.com)";
                ```
                """;

        DocumentBlock code = MarkdownDocumentParser.parse(md).getFirst();

        assertEquals(DocumentBlock.Type.CODE, code.type());
        assertEquals("java", code.meta());
        assertTrue(code.text().contains("# 这行是注释，不是标题"));
        assertTrue(code.text().contains("[文档](https://example.com)"),
                "代码块内的 Markdown 语法必须原样保留");
    }

    @Test
    void unclosedFenceConsumesRemainingLines() {
        String md = "```sql\nselect 1;\nselect 2;\n";

        List<DocumentBlock> blocks = MarkdownDocumentParser.parse(md);

        assertEquals(1, blocks.size());
        assertEquals(DocumentBlock.Type.CODE, blocks.getFirst().type());
        assertTrue(blocks.getFirst().text().contains("select 2;"));
    }

    @Test
    void footnoteDefinitionSupportsIndentedContinuation() {
        String md = """
                正文引用[^1]。

                [^1]: 第一条脚注
                    续行内容
                """;

        List<DocumentBlock> blocks = MarkdownDocumentParser.parse(md);

        assertEquals(2, blocks.size());
        assertEquals(DocumentBlock.Type.PARAGRAPH, blocks.get(0).type());
        assertTrue(blocks.get(0).text().contains("[^1]"), "脚注引用必须保留");
        DocumentBlock footnote = blocks.get(1);
        assertEquals(DocumentBlock.Type.FOOTNOTE, footnote.type());
        assertEquals("1", footnote.meta());
        assertEquals("第一条脚注 续行内容", footnote.text());
    }

    @Test
    void frontmatterIsDropped() {
        String md = """
                ---
                title: demo
                tags: [a, b]
                ---
                # 标题
                正文
                """;

        List<DocumentBlock> blocks = MarkdownDocumentParser.parse(md);

        assertEquals(2, blocks.size());
        assertEquals(DocumentBlock.Type.HEADING, blocks.get(0).type());
        assertEquals(1, blocks.get(0).level());
        assertEquals("标题", blocks.get(0).text());
        assertEquals("正文", blocks.get(1).text());
    }

    @Test
    void softLineBreaksMergeWithoutSpaceBetweenChinese() {
        String md = "第一行中文\n第二行中文\n";

        DocumentBlock paragraph = MarkdownDocumentParser.parse(md).getFirst();

        assertEquals("第一行中文第二行中文", paragraph.text());
    }

    @Test
    void englishSoftBreaksKeepSingleSpace() {
        String md = "first line\nsecond line\n";

        assertEquals("first line second line", MarkdownDocumentParser.parse(md).getFirst().text());
    }

    @Test
    void imageKeepsAltAndLinkKeepsLabel() {
        String md = "![结构图](https://x/a.png) 详见 [源码](https://x/b)";

        String text = MarkdownDocumentParser.parse(md).getFirst().text();

        assertTrue(text.contains("结构图"));
        assertTrue(text.contains("源码"));
        assertFalse(text.contains("https://"));
    }

    @Test
    void listAndQuoteKeepStructure() {
        String md = """
                - 第一项
                - 第二项
                > 引用内容
                """;

        List<DocumentBlock> blocks = MarkdownDocumentParser.parse(md);

        assertEquals(3, blocks.size());
        assertEquals(DocumentBlock.Type.LIST_ITEM, blocks.get(0).type());
        assertEquals(DocumentBlock.Type.LIST_ITEM, blocks.get(1).type());
        assertEquals(DocumentBlock.Type.QUOTE, blocks.get(2).type());
    }

    @Test
    void pipeParagraphFollowedByRuleIsNotTable() {
        String md = """
                文字 | 说明
                ---
                """;

        List<DocumentBlock> blocks = MarkdownDocumentParser.parse(md);

        assertEquals(1, blocks.size());
        assertEquals(DocumentBlock.Type.PARAGRAPH, blocks.getFirst().type());
    }

    @Test
    void rendersTableAndCodeAsMarkdown() {
        List<DocumentBlock> blocks = List.of(
                DocumentBlock.heading(2, "标题"),
                DocumentBlock.table("| a | b |\n| --- | --- |\n| 1 | 2 |"),
                DocumentBlock.code("int a = 1;", "java")
        );

        String text = DocumentTextRenderer.render(blocks);

        assertTrue(text.startsWith("## 标题"));
        assertTrue(text.contains("```java\nint a = 1;\n```"));
        assertTrue(text.contains("| 1 | 2 |"));
    }
}
