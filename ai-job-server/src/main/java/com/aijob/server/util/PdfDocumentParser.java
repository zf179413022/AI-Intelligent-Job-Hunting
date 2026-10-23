package com.aijob.server.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * PDF 布局感知解析器：按字符坐标重建行、列与块，输出表格/代码/脚注/标题/段落。
 * <p>
 * 纯文本抽取会把多列表格压成一条流水账；本解析器按 x 间隙切分单元格，
 * 连续且列数一致的行视为表格并渲染成 Markdown，等宽字体行视为代码，
 * 页面底部的小字号行视为脚注。错误定位到「第 N 页」。
 */
public final class PdfDocumentParser {

    private static final Pattern MONO_FONT =
            Pattern.compile("(?i).*(courier|mono|consol|menlo|code|fixed|typewriter).*");
    private static final Pattern PAGE_NUMBER = Pattern.compile("^[\\s\\-–—]*\\d{1,4}[\\s\\-–—]*$");
    private static final float CELL_GAP_RATIO = 2.0f;
    private static final float MIN_CELL_GAP = 6.0f;
    private static final float PARAGRAPH_GAP_RATIO = 1.75f;

    private PdfDocumentParser() {
    }

    public record ParseResult(List<DocumentBlock> blocks, int pageCount, List<String> warnings) {
    }

    public static ParseResult parse(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            if (document.isEncrypted()) {
                throw new DocumentParseException("PDF 已加密，无法解析");
            }
            LayoutStripper stripper = new LayoutStripper();
            stripper.setSortByPosition(true);
            stripper.getText(document);
            return new ParseResult(
                    List.copyOf(stripper.blocks),
                    document.getNumberOfPages(),
                    List.copyOf(stripper.warnings)
            );
        } catch (IOException e) {
            throw new DocumentParseException("PDF 读取失败：" + e.getMessage(), e);
        }
    }

    // ---------- 字符/行模型 ----------

    private record Glyph(String text, float x, float endX, float y, float size, String font) {
        boolean isMono() {
            return font != null && MONO_FONT.matcher(font).matches();
        }
    }

    private record Row(List<Glyph> glyphs) {
        float x() {
            return glyphs.getFirst().x();
        }

        float y() {
            return glyphs.getFirst().y();
        }

        float size() {
            float sum = 0;
            for (Glyph glyph : glyphs) {
                sum += glyph.size();
            }
            return sum / glyphs.size();
        }

        boolean mono() {
            return glyphs.stream().allMatch(Glyph::isMono);
        }

        String text() {
            StringBuilder sb = new StringBuilder();
            Glyph prev = null;
            for (Glyph glyph : glyphs) {
                if (prev != null) {
                    float gap = glyph.x - prev.endX;
                    if (gap > 0.25f * Math.max(1f, glyph.size())) {
                        sb.append(' ');
                    }
                }
                sb.append(glyph.text);
                prev = glyph;
            }
            return sb.toString().replaceAll("\\s+", " ").trim();
        }
    }

    // ---------- PDFTextStripper 子类 ----------

    private static final class LayoutStripper extends PDFTextStripper {

        private final List<DocumentBlock> blocks = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private List<Row> pageRows = new ArrayList<>();
        private List<Row> currentRows = new ArrayList<>();
        private int pageNo;
        private float pageHeight;
        private float pageWidth = 595f;
        private float bodySize = 10f;

        LayoutStripper() throws IOException {
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            pageNo++;
            pageHeight = page.getCropBox().getHeight();
            pageWidth = page.getCropBox().getWidth();
            pageRows.clear();
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            if (positions == null || positions.isEmpty()) {
                return;
            }
            List<Glyph> glyphs = new ArrayList<>(positions.size());
            for (TextPosition position : positions) {
                String unicode = position.getUnicode();
                if (unicode == null || unicode.isEmpty()) {
                    continue;
                }
                glyphs.add(new Glyph(
                        unicode,
                        position.getXDirAdj(),
                        position.getXDirAdj() + position.getWidthDirAdj(),
                        position.getYDirAdj(),
                        position.getFontSizeInPt(),
                        position.getFont() == null ? null : position.getFont().getName()
                ));
            }
            if (!glyphs.isEmpty()) {
                pageRows.add(new Row(glyphs));
            }
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            try {
                processPage();
            } catch (RuntimeException e) {
                warnings.add("第 " + pageNo + " 页解析失败：" + safeMessage(e));
            }
            pageRows.clear();
            super.endPage(page);
        }

        private void processPage() {
            if (pageRows.isEmpty()) {
                return;
            }
            pageRows.sort(Comparator.comparingDouble(Row::y));
            pageRows = mergeRowsByBaseline(pageRows);
            bodySize = medianSize(pageRows);

            float splitX = detectColumnSplit();
            if (Float.isNaN(splitX)) {
                processRows(pageRows);
                return;
            }
            // 双栏：先读完左栏再读右栏，避免左右栏被拼进同一行、也避免被误判成表格
            List<Row> left = new ArrayList<>();
            List<Row> right = new ArrayList<>();
            for (Row row : pageRows) {
                List<Glyph> leftGlyphs = new ArrayList<>();
                List<Glyph> rightGlyphs = new ArrayList<>();
                for (Glyph glyph : row.glyphs()) {
                    (glyph.x() < splitX ? leftGlyphs : rightGlyphs).add(glyph);
                }
                if (!leftGlyphs.isEmpty()) {
                    left.add(new Row(leftGlyphs));
                }
                if (!rightGlyphs.isEmpty()) {
                    right.add(new Row(rightGlyphs));
                }
            }
            processRows(left);
            processRows(right);
        }

        private void processRows(List<Row> rows) {
            currentRows = rows;
            int i = 0;
            while (i < rows.size()) {
                Row row = rows.get(i);
                String text = row.text();
                if (text.isBlank()) {
                    i++;
                    continue;
                }
                if (isPageNumber(row, text)) {
                    i++;
                    continue;
                }
                if (row.mono()) {
                    i = readCode(i);
                    continue;
                }
                if (isFootnote(row, text)) {
                    blocks.add(DocumentBlock.footnote(String.valueOf(pageNo) + "-" + (i + 1), text));
                    i++;
                    continue;
                }
                if (i + 1 < rows.size() && isTableRun(i)) {
                    i = readTable(i);
                    continue;
                }
                if (isHeading(row)) {
                    blocks.add(DocumentBlock.heading(headingLevel(row), text));
                    i++;
                    continue;
                }
                i = readParagraph(i);
            }
        }

        /**
         * 单栏文档偶尔有「标题 + 日期」这种大间隙行，但占比低；
         * 双栏文档超过 60% 的行都在页面中部同一 x 处断开。两者据此区分。
         */
        private float detectColumnSplit() {
            if (pageRows.size() < 6) {
                return Float.NaN;
            }
            List<Float> gapXs = new ArrayList<>();
            int rowsWithGap = 0;
            for (Row row : pageRows) {
                Glyph prev = null;
                float maxGap = 0;
                float atX = 0;
                int gapCount = 0;
                for (Glyph glyph : row.glyphs()) {
                    if (prev != null) {
                        float gap = glyph.x() - prev.endX();
                        float threshold = Math.max(MIN_CELL_GAP, 2.5f * Math.max(prev.size(), glyph.size()));
                        if (gap > threshold) {
                            gapCount++;
                            if (gap > maxGap) {
                                maxGap = gap;
                                atX = prev.endX();
                            }
                        }
                    }
                    prev = glyph;
                }
                if (gapCount > 0) {
                    rowsWithGap++;
                    if (gapCount <= 2) {
                        gapXs.add(atX);
                    }
                }
            }
            if (rowsWithGap < pageRows.size() * 0.6f || gapXs.size() < pageRows.size() * 0.5f) {
                return Float.NaN;
            }
            double mean = gapXs.stream().mapToDouble(Float::doubleValue).average().orElse(0);
            double variance = gapXs.stream()
                    .mapToDouble(x -> (x - mean) * (x - mean))
                    .average()
                    .orElse(0);
            if (Math.sqrt(variance) > pageWidth * 0.06) {
                return Float.NaN;
            }
            // 中缝落在页面中部才像分栏；表格的列间隙通常落在 25% 一带，会被排除
            if (mean < pageWidth * 0.35 || mean > pageWidth * 0.65) {
                return Float.NaN;
            }
            return (float) mean;
        }

        private int readCode(int start) {
            int i = start;
            List<String> body = new ArrayList<>();
            while (i < currentRows.size() && currentRows.get(i).mono()) {
                String line = currentRows.get(i).text();
                if (!line.isBlank()) {
                    body.add(line);
                }
                i++;
            }
            if (!body.isEmpty()) {
                blocks.add(DocumentBlock.code(String.join("\n", body), ""));
            }
            return i;
        }

        private int readTable(int start) {
            int cols = cells(currentRows.get(start)).size();
            List<Row> rows = new ArrayList<>();
            int i = start;
            while (i < currentRows.size()) {
                Row row = currentRows.get(i);
                if (!isTableRow(row) || cells(row).size() != cols) {
                    break;
                }
                rows.add(row);
                i++;
            }
            if (rows.size() < 2) {
                // 单行多单元格通常是「标题 + 日期」，退回段落，避免误判
                return readParagraph(start);
            }
            StringBuilder table = new StringBuilder();
            for (int r = 0; r < rows.size(); r++) {
                List<String> cells = cells(rows.get(r));
                table.append('|');
                for (String cell : cells) {
                    table.append(' ').append(cell.replace("|", "\\|")).append(" |");
                }
                if (r == 0) {
                    table.append('\n').append('|');
                    for (int c = 0; c < cols; c++) {
                        table.append(" --- |");
                    }
                }
                if (r < rows.size() - 1) {
                    table.append('\n');
                }
            }
            blocks.add(DocumentBlock.table(table.toString()));
            return i;
        }

        private int readParagraph(int start) {
            StringBuilder text = new StringBuilder();
            int i = start;
            float previousY = Float.NaN;
            while (i < currentRows.size()) {
                Row row = currentRows.get(i);
                String line = row.text();
                if (line.isBlank() || row.mono() || isFootnote(row, line) || isPageNumber(row, line)) {
                    break;
                }
                if (i != start && isTableRun(i)) {
                    break;
                }
                if (!Float.isNaN(previousY) && row.y() - previousY > PARAGRAPH_GAP_RATIO * bodySize) {
                    break;
                }
                if (!text.isEmpty()) {
                    text.append(needsSpace(text.charAt(text.length() - 1), line.charAt(0)) ? " " : "");
                }
                text.append(line);
                previousY = row.y();
                i++;
            }
            String paragraph = text.toString().trim();
            if (!paragraph.isBlank()) {
                blocks.add(DocumentBlock.paragraph(paragraph));
            }
            return Math.max(i, start + 1);
        }

        private List<String> cells(Row row) {
            List<String> cells = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            Glyph prev = null;
            for (Glyph glyph : row.glyphs()) {
                if (prev != null) {
                    float gap = glyph.x - prev.endX;
                    float threshold = Math.max(MIN_CELL_GAP, CELL_GAP_RATIO * Math.max(prev.size(), glyph.size()));
                    if (gap > threshold) {
                        cells.add(current.toString().trim());
                        current.setLength(0);
                    }
                }
                current.append(glyph.text);
                prev = glyph;
            }
            cells.add(current.toString().trim());
            return cells;
        }

        private boolean isTableRun(int start) {
            if (!isTableRow(currentRows.get(start))) {
                return false;
            }
            int cols = cells(currentRows.get(start)).size();
            return start + 1 < currentRows.size()
                    && isTableRow(currentRows.get(start + 1))
                    && cells(currentRows.get(start + 1)).size() == cols;
        }

        private boolean isTableRow(Row row) {
            return cells(row).size() >= 2;
        }

        private boolean isFootnote(Row row, String text) {
            if (row.size() > bodySize * 0.92f) {
                return false;
            }
            return row.y() > pageHeight * 0.80f;
        }

        private boolean isPageNumber(Row row, String text) {
            return PAGE_NUMBER.matcher(text).matches() && row.y() > pageHeight * 0.9f;
        }

        private boolean isHeading(Row row) {
            return row.size() > bodySize * 1.18f && row.text().length() <= 60 && !isTableRow(row);
        }

        private int headingLevel(Row row) {
            if (row.size() > bodySize * 1.5f) {
                return 1;
            }
            return row.size() > bodySize * 1.3f ? 2 : 3;
        }

        private static float medianSize(List<Row> rows) {
            List<Float> sizes = new ArrayList<>();
            for (Row row : rows) {
                for (Glyph glyph : row.glyphs()) {
                    sizes.add(glyph.size());
                }
            }
            if (sizes.isEmpty()) {
                return 10f;
            }
            sizes.sort(Float::compare);
            return sizes.get(sizes.size() / 2);
        }

        /** 同一基线的多段文本（PDFBox 可能分多次 writeString 交付）按 x 合并成一行。 */
        private static List<Row> mergeRowsByBaseline(List<Row> rows) {
            List<Row> merged = new ArrayList<>(rows.size());
            for (Row row : rows) {
                if (!merged.isEmpty()) {
                    Row last = merged.getLast();
                    if (Math.abs(last.y() - row.y()) <= 2.0f) {
                        List<Glyph> glyphs = new ArrayList<>(last.glyphs().size() + row.glyphs().size());
                        glyphs.addAll(last.glyphs());
                        glyphs.addAll(row.glyphs());
                        glyphs.sort(Comparator.comparingDouble(Glyph::x));
                        merged.set(merged.size() - 1, new Row(glyphs));
                        continue;
                    }
                }
                List<Glyph> glyphs = new ArrayList<>(row.glyphs());
                glyphs.sort(Comparator.comparingDouble(Glyph::x));
                merged.add(new Row(glyphs));
            }
            return merged;
        }

        private static boolean needsSpace(char last, char first) {
            return !(isCjk(last) || isCjk(first));
        }

        private static boolean isCjk(char c) {
            return (c >= '\u4E00' && c <= '\u9FFF')
                    || (c >= '\u3000' && c <= '\u303F')
                    || (c >= '\uFF00' && c <= '\uFFEF');
        }

        private static String safeMessage(Exception e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = e.getClass().getSimpleName();
            }
            return message.length() > 200 ? message.substring(0, 200) : message;
        }
    }
}
