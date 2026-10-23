package com.aijob.server.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 块级解析器：把 Markdown 还原为 {@link DocumentBlock} 序列。
 * <p>
 * 覆盖：ATX 标题、段落（软换行合并）、列表、引用、围栏代码块、GFM 表格、脚注定义、
 * YAML frontmatter、HTML 标签、图片/链接/行内代码。
 * <p>
 * 关键约束：代码块正文绝不参与行内规则，避免 {@code # 注释}、{@code [x](url)} 被误改。
 */
public final class MarkdownDocumentParser {

    private static final Pattern FRONTMATTER_OPEN = Pattern.compile("^---\\s*$|^\\.\\.\\.\\s*$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})\\s*([^\\s`~]*)");
    private static final Pattern FOOTNOTE_DEF = Pattern.compile("^ {0,3}\\[\\^([^\\]\\s]+)]\\s*:\\s?(.*)$");
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s{0,3}([-*+]|\\d{1,9}[.)])\\s+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}>\\s?(.*)$");
    private static final Pattern THEMATIC_BREAK =
            Pattern.compile("^ {0,3}((\\*\\s*){3,}|(-\\s*){3,}|(_\\s*){3,})$");
    private static final Pattern TABLE_DELIM_CELL = Pattern.compile("^:?-{3,}:?$");
    private static final Pattern HTML_TAG = Pattern.compile("<\\/?[a-zA-Z][^<>]*>");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern INLINE_LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern REF_LINK = Pattern.compile("\\[([^\\]]*)]\\[[^\\]]*]");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern BOLD_UNDERSCORE = Pattern.compile("__(.+?)__");
    private static final Pattern STRIKETHROUGH = Pattern.compile("~~(.+?)~~");

    private MarkdownDocumentParser() {
    }

    public static List<DocumentBlock> parse(String raw) {
        List<DocumentBlock> blocks = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return blocks;
        }
        List<String> lines = splitLines(stripBom(raw));
        int i = skipFrontmatter(lines);
        StringBuilder paragraph = new StringBuilder();

        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.isBlank()) {
                flushParagraph(blocks, paragraph);
                i++;
                continue;
            }

            Matcher fence = FENCE.matcher(line);
            if (fence.find()) {
                flushParagraph(blocks, paragraph);
                i = readFenceCode(lines, i, fence, blocks);
                continue;
            }

            Matcher footnote = FOOTNOTE_DEF.matcher(line);
            if (footnote.matches()) {
                flushParagraph(blocks, paragraph);
                i = readFootnote(lines, i, footnote, blocks);
                continue;
            }

            if (isTableHeader(lines, i)) {
                flushParagraph(blocks, paragraph);
                i = readTable(lines, i, blocks);
                continue;
            }

            Matcher heading = HEADING.matcher(line);
            if (heading.find()) {
                flushParagraph(blocks, paragraph);
                int level = heading.group(1).length();
                String text = inlineText(heading.group(2));
                if (!text.isBlank()) {
                    blocks.add(DocumentBlock.heading(level, text));
                }
                i++;
                continue;
            }

            if (THEMATIC_BREAK.matcher(line).matches()) {
                flushParagraph(blocks, paragraph);
                i++;
                continue;
            }

            Matcher quote = QUOTE.matcher(line);
            if (quote.find()) {
                flushParagraph(blocks, paragraph);
                i = readQuote(lines, i, blocks);
                continue;
            }

            Matcher list = LIST_ITEM.matcher(line);
            if (list.matches()) {
                flushParagraph(blocks, paragraph);
                String text = inlineText(list.group(2));
                if (!text.isBlank()) {
                    blocks.add(DocumentBlock.listItem(text));
                }
                i++;
                continue;
            }

            appendParagraph(paragraph, inlineText(line));
            i++;
        }
        flushParagraph(blocks, paragraph);
        return blocks;
    }

    // ---------- 块读取 ----------

    private static int skipFrontmatter(List<String> lines) {
        if (lines.isEmpty() || !FRONTMATTER_OPEN.matcher(lines.getFirst()).matches()) {
            return 0;
        }
        for (int i = 1; i < lines.size(); i++) {
            if (FRONTMATTER_OPEN.matcher(lines.get(i)).matches()) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int readFenceCode(List<String> lines, int start, Matcher open, List<DocumentBlock> out) {
        String marker = open.group(1);
        String lang = open.group(2);
        char markerChar = marker.charAt(0);
        int markerLen = marker.length();
        int i = start + 1;
        List<String> body = new ArrayList<>();
        boolean closed = false;

        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.length() >= markerLen
                    && trimmed.chars().allMatch(c -> c == markerChar)) {
                closed = true;
                i++;
                break;
            }
            body.add(stripCommonIndent(line, open.start(1)));
            i++;
        }
        if (!body.isEmpty() || closed) {
            String content = String.join("\n", body);
            if (!content.isBlank()) {
                out.add(DocumentBlock.code(content, lang));
            }
        }
        return i;
    }

    private static int readFootnote(List<String> lines, int start, Matcher def, List<DocumentBlock> out) {
        String id = def.group(1);
        StringBuilder body = new StringBuilder(def.group(2).trim());
        int i = start + 1;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank()) {
                break;
            }
            if (line.startsWith("    ") || line.startsWith("\t")) {
                body.append(' ').append(line.trim());
                i++;
                continue;
            }
            break;
        }
        String text = inlineText(body.toString());
        if (!text.isBlank()) {
            out.add(DocumentBlock.footnote(id, text));
        }
        return i;
    }

    private static int readTable(List<String> lines, int start, List<DocumentBlock> out) {
        List<String> header = splitRow(lines.get(start));
        List<String> delimiter = splitRow(lines.get(start + 1));
        int cols = Math.max(header.size(), delimiter.size());
        if (cols == 0) {
            return start + 1;
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(normalizeCells(header, cols));
        int i = start + 2;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank() || !line.contains("|")) {
                break;
            }
            rows.add(normalizeCells(splitRow(line), cols));
            i++;
        }
        out.add(DocumentBlock.table(renderTable(rows)));
        return i;
    }

    private static int readQuote(List<String> lines, int start, List<DocumentBlock> out) {
        StringBuilder body = new StringBuilder();
        int i = start;
        while (i < lines.size()) {
            Matcher quote = QUOTE.matcher(lines.get(i));
            if (!quote.find()) {
                break;
            }
            appendParagraph(body, inlineText(quote.group(1)));
            i++;
        }
        String text = body.toString().trim();
        if (!text.isBlank()) {
            out.add(DocumentBlock.quote(text));
        }
        return i;
    }

    // ---------- 表格 ----------

    private static boolean isTableHeader(List<String> lines, int index) {
        if (index + 1 >= lines.size()) {
            return false;
        }
        String head = lines.get(index);
        if (!head.contains("|")) {
            return false;
        }
        List<String> delimiter = splitRow(lines.get(index + 1));
        List<String> headCells = splitRow(head);
        // GFM 要求表头与分隔行列数一致；不一致时按普通文本处理，避免把「文字 | 说明」+「---」误判成 1 列表格
        if (delimiter.isEmpty() || delimiter.size() != headCells.size()) {
            return false;
        }
        for (String cell : delimiter) {
            if (!TABLE_DELIM_CELL.matcher(cell.replace(" ", "")).matches()) {
                return false;
            }
        }
        return true;
    }

    static List<String> splitRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|") && !s.endsWith("\\|")) {
            s = s.substring(0, s.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inCode = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                current.append(c).append(s.charAt(i + 1));
                i++;
                continue;
            }
            if (c == '`') {
                inCode = !inCode;
                current.append(c);
                continue;
            }
            if (c == '|' && !inCode) {
                cells.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private static List<String> normalizeCells(List<String> cells, int cols) {
        List<String> normalized = new ArrayList<>(cols);
        for (int i = 0; i < cols; i++) {
            normalized.add(i < cells.size() ? inlineText(cells.get(i)) : "");
        }
        return normalized;
    }

    private static String renderTable(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        appendTableRow(sb, rows.getFirst());
        sb.append('\n');
        appendDelimiterRow(sb, rows.getFirst().size());
        for (int r = 1; r < rows.size(); r++) {
            sb.append('\n');
            appendTableRow(sb, rows.get(r));
        }
        return sb.toString();
    }

    private static void appendTableRow(StringBuilder sb, List<String> cells) {
        sb.append('|');
        for (String cell : cells) {
            sb.append(' ').append(cell.isBlank() ? "" : escapeCell(cell)).append(" |");
        }
    }

    private static String escapeCell(String cell) {
        return cell.replace("|", "\\|");
    }

    private static void appendDelimiterRow(StringBuilder sb, int cols) {
        sb.append('|');
        for (int i = 0; i < cols; i++) {
            sb.append(" --- |");
        }
    }

    // ---------- 行内 ----------

    /**
     * 行内清理：保护代码 span 后，图片取 alt、链接取文字、去 HTML 标签与强调标记、去反斜杠转义。
     */
    static String inlineText(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        String text = source;

        List<String> codeSpans = new ArrayList<>();
        text = protectCodeSpans(text, codeSpans);

        text = IMAGE.matcher(text).replaceAll("$1");
        text = INLINE_LINK.matcher(text).replaceAll("$1");
        text = REF_LINK.matcher(text).replaceAll("$1");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = BOLD.matcher(text).replaceAll("$1");
        text = BOLD_UNDERSCORE.matcher(text).replaceAll("$1");
        text = STRIKETHROUGH.matcher(text).replaceAll("$1");

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && !Character.isLetterOrDigit(text.charAt(i + 1))) {
                out.append(text.charAt(++i));
                continue;
            }
            out.append(c);
        }
        return restoreCodeSpans(out.toString(), codeSpans).trim();
    }

    private static String protectCodeSpans(String text, List<String> spans) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != '`') {
                out.append(c);
                i++;
                continue;
            }
            int run = 1;
            while (i + run < text.length() && text.charAt(i + run) == '`') {
                run++;
            }
            String fence = "`".repeat(run);
            int end = text.indexOf(fence, i + run);
            if (end < 0) {
                out.append(text, i, i + run);
                i += run;
                continue;
            }
            spans.add(text.substring(i, end + run));
            out.append('\u0000').append(spans.size() - 1).append('\u0000');
            i = end + run;
        }
        return out.toString();
    }

    private static String restoreCodeSpans(String text, List<String> spans) {
        if (spans.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf('\u0000', i);
            if (start < 0) {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, start);
            int end = text.indexOf('\u0000', start + 1);
            if (end < 0) {
                out.append(text, start, text.length());
                break;
            }
            int index = Integer.parseInt(text.substring(start + 1, end));
            out.append(spans.get(index));
            i = end + 1;
        }
        return out.toString();
    }

    // ---------- 工具 ----------

    private static void appendParagraph(StringBuilder paragraph, String text) {
        if (text.isBlank()) {
            return;
        }
        if (paragraph.length() > 0) {
            char last = paragraph.charAt(paragraph.length() - 1);
            char first = text.charAt(0);
            if (needsSpace(last, first)) {
                paragraph.append(' ');
            }
        }
        paragraph.append(text);
    }

    private static boolean needsSpace(char last, char first) {
        return !(isCjk(last) || isCjk(first));
    }

    private static boolean isCjk(char c) {
        return (c >= '\u4E00' && c <= '\u9FFF')
                || (c >= '\u3000' && c <= '\u303F')
                || (c >= '\uFF00' && c <= '\uFFEF');
    }

    private static void flushParagraph(List<DocumentBlock> blocks, StringBuilder paragraph) {
        String text = paragraph.toString().trim();
        if (!text.isBlank()) {
            blocks.add(DocumentBlock.paragraph(text));
        }
        paragraph.setLength(0);
    }

    private static String stripCommonIndent(String line, int indent) {
        int cut = 0;
        while (cut < line.length() && cut < indent && line.charAt(cut) == ' ') {
            cut++;
        }
        return line.substring(cut);
    }

    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
    }

    private static List<String> splitLines(String text) {
        return List.of(text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
    }
}
