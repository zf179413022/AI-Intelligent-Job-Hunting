package com.aijob.server.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfDocumentParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesHeadingParagraphTableCodeAndFootnote() throws IOException {
        Path pdf = layoutPdf(tempDir.resolve("layout.pdf"));

        PdfDocumentParser.ParseResult result = PdfDocumentParser.parse(pdf);

        assertEquals(1, result.pageCount());
        assertTrue(result.warnings().isEmpty());

        List<DocumentBlock> blocks = result.blocks();
        assertTrue(hasType(blocks, DocumentBlock.Type.HEADING), "缺少标题块：" + blocks);

        DocumentBlock table = firstOf(blocks, DocumentBlock.Type.TABLE);
        assertTrue(table.text().contains("| Language | Concurrency | Scenario |"), table.text());
        assertTrue(table.text().contains("| Java | Thread + Virtual | Backend |"), table.text());
        assertTrue(table.text().contains("| Go | goroutine | CloudNative |"), table.text());

        DocumentBlock code = firstOf(blocks, DocumentBlock.Type.CODE);
        assertTrue(code.text().contains("HashMap"), code.text());

        DocumentBlock footnote = firstOf(blocks, DocumentBlock.Type.FOOTNOTE);
        assertTrue(footnote.text().contains("JDK 21"), footnote.text());
        assertTrue(footnote.meta().startsWith("1-"), "脚注必须定位到页：" + footnote.meta());
    }

    @Test
    void emptyScannedPdfYieldsNoBlocks() throws IOException {
        Path pdf = tempDir.resolve("blank.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(pdf.toFile());
        }

        PdfDocumentParser.ParseResult result = PdfDocumentParser.parse(pdf);

        assertEquals(1, result.pageCount());
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    void facadeRendersStructuredText() throws IOException {
        Path pdf = layoutPdf(tempDir.resolve("facade.pdf"));

        DocumentExtractResult result = DocumentTextExtractor.extractDetailed(pdf, "PDF");

        assertTrue(result.text().contains("| --- | --- |"), "渲染结果应保留 Markdown 表格");
        assertTrue(result.text().contains("```"), "渲染结果应保留代码围栏");
        assertEquals(1, result.pageCount());
    }

    @Test
    void twoColumnPdfIsReadColumnByColumnNotAsTable() throws IOException {
        Path pdf = twoColumnPdf(tempDir.resolve("two-column.pdf"));

        PdfDocumentParser.ParseResult result = PdfDocumentParser.parse(pdf);

        assertTrue(result.blocks().stream().noneMatch(block -> block.type() == DocumentBlock.Type.TABLE),
                "双栏不应被误判为表格：" + result.blocks());
        String all = String.join("\n",
                result.blocks().stream().map(DocumentBlock::text).toList());
        assertTrue(all.contains("LEFT column line 5"), all);
        assertTrue(all.contains("RIGHT column line 5"), all);
        assertTrue(all.indexOf("LEFT") < all.indexOf("RIGHT"), "应先读左栏再读右栏：" + all);
    }

    private static boolean hasType(List<DocumentBlock> blocks, DocumentBlock.Type type) {
        return blocks.stream().anyMatch(block -> block.type() == type);
    }

    private static DocumentBlock firstOf(List<DocumentBlock> blocks, DocumentBlock.Type type) {
        return blocks.stream()
                .filter(block -> block.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 " + type + " 块：" + blocks));
    }

    /** 生成版式夹具：标题 / 段落 / 3 列表格 / 等宽代码 / 页脚脚注。 */
    private static Path layoutPdf(Path target) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float height = page.getMediaBox().getHeight();

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                text(content, 50, height - 60, 18, Standard14Fonts.FontName.HELVETICA_BOLD,
                        "Java Interview Notes");
                text(content, 50, height - 100, 10, Standard14Fonts.FontName.HELVETICA,
                        "This note compares HashMap and thread pool details.");

                float[] columns = {50, 200, 360};
                float y = height - 150;
                tableRow(content, columns, y, "Language", "Concurrency", "Scenario");
                y -= 20;
                tableRow(content, columns, y, "Java", "Thread + Virtual", "Backend");
                y -= 20;
                tableRow(content, columns, y, "Go", "goroutine", "CloudNative");
                y -= 40;

                text(content, 50, y, 9, Standard14Fonts.FontName.COURIER,
                        "Map<String, Object> map = new HashMap<>();");
                y -= 12;
                text(content, 50, y, 9, Standard14Fonts.FontName.COURIER,
                        "map.put(\"k\", 1); // # stays code");

                text(content, 50, 50, 7.5f, Standard14Fonts.FontName.HELVETICA,
                        "[1] Virtual threads are final in JDK 21.");
            }

            doc.save(target.toFile());
        }
        return target;
    }

    /** 双栏夹具：左右各 6 行，中缝处大间隙，用于验证分栏而不是误判成表格。 */
    private static Path twoColumnPdf(Path target) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float height = page.getMediaBox().getHeight();
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 6; i++) {
                    float y = height - 80 - i * 16;
                    text(content, 50, y, 10, Standard14Fonts.FontName.HELVETICA,
                            "LEFT column line " + i + " with some words");
                    text(content, 330, y, 10, Standard14Fonts.FontName.HELVETICA,
                            "RIGHT column line " + i + " with some words");
                }
            }
            doc.save(target.toFile());
        }
        return target;
    }

    private static void tableRow(PDPageContentStream content, float[] columns, float y, String... cells)
            throws IOException {
        for (int i = 0; i < cells.length; i++) {
            text(content, columns[i], y, 10, Standard14Fonts.FontName.HELVETICA, cells[i]);
        }
    }

    private static void text(PDPageContentStream content, float x, float y, float size,
                             Standard14Fonts.FontName font, String value) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(font), size);
        content.newLineAtOffset(x, y);
        content.showText(value);
        content.endText();
    }
}
