package com.aijob.server.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 知识库分块：目标约 700 字符，overlap 约 100。
 * <p>
 * 两种入口：
 * <ul>
 *   <li>{@link #chunk(String)}：纯文本，按段落贪心合并（兼容旧调用方）</li>
 *   <li>{@link #chunkBlocks(List)}：结构化块，表格/代码整体保留，块前附带标题路径</li>
 * </ul>
 */
public final class KnowledgeTextChunker {

    public static final int DEFAULT_MAX_CHARS = 700;
    public static final int DEFAULT_OVERLAP = 100;
    /** chunk 前保留的标题层级数，过多会挤占正文预算。 */
    private static final int MAX_HEADING_DEPTH = 2;

    private KnowledgeTextChunker() {
    }

    public static List<String> chunk(String text) {
        return chunk(text, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP);
    }

    public static List<String> chunk(String text, int maxChars, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        maxChars = normalizeMax(maxChars);
        overlap = normalizeOverlap(overlap, maxChars);

        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();

        List<String> paragraphs = splitParagraphs(normalized);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                continue;
            }

            if (p.length() > maxChars) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                chunks.addAll(splitLongText(p, maxChars, overlap));
                continue;
            }

            if (current.length() == 0) {
                current.append(p);
            } else if (current.length() + 1 + p.length() <= maxChars) {
                current.append('\n').append(p);
            } else {
                chunks.add(current.toString().trim());
                String overlapText = takeOverlap(current.toString(), overlap);
                current.setLength(0);
                if (!overlapText.isEmpty()) {
                    current.append(overlapText).append('\n');
                }
                current.append(p);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks.stream().filter(s -> !s.isBlank()).toList();
    }

    // ---------- 结构化分块 ----------

    public static List<String> chunkBlocks(List<DocumentBlock> blocks) {
        return chunkBlocks(blocks, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP);
    }

    /**
     * 表格/代码块不拆分，段落按字符预算合并；每个 chunk 带「最近标题路径」前缀，
     * 让脱离上下文的 chunk 也能被检索命中。
     */
    public static List<String> chunkBlocks(List<DocumentBlock> blocks, int maxChars, int overlap) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        maxChars = normalizeMax(maxChars);
        overlap = normalizeOverlap(overlap, maxChars);

        List<String> chunks = new ArrayList<>();
        Deque<DocumentBlock> headings = new ArrayDeque<>();
        StringBuilder current = new StringBuilder();

        for (DocumentBlock block : blocks) {
            if (block == null || block.isBlank()) {
                continue;
            }
            if (block.type() == DocumentBlock.Type.HEADING) {
                flush(current, chunks);
                while (!headings.isEmpty() && headings.peekLast().level() >= block.level()) {
                    headings.removeLast();
                }
                headings.addLast(block);
                continue;
            }

            String prefix = headingPrefix(headings);
            if (block.isAtomic()) {
                flush(current, chunks);
                String body = DocumentTextRenderer.renderBlock(block);
                if (body.length() + prefix.length() <= maxChars) {
                    chunks.add(prefix + body);
                } else {
                    chunks.addAll(splitAtomic(block, prefix, maxChars));
                }
                continue;
            }

            appendText(current, prefix, DocumentTextRenderer.renderBlock(block), maxChars, overlap, chunks);
        }
        flush(current, chunks);
        return chunks.stream().filter(s -> !s.isBlank()).toList();
    }

    private static void appendText(StringBuilder current, String prefix, String text,
                                   int maxChars, int overlap, List<String> chunks) {
        int textBudget = Math.max(100, maxChars - prefix.length());
        if (text.length() > textBudget) {
            flush(current, chunks);
            for (String part : splitLongText(text, textBudget, overlap)) {
                chunks.add(prefix + part);
            }
            return;
        }
        if (current.length() == 0) {
            current.append(prefix);
        }
        if (current.length() + 1 + text.length() <= maxChars) {
            if (current.length() > prefix.length()) {
                current.append('\n');
            }
            current.append(text);
            return;
        }
        chunks.add(current.toString().trim());
        String overlapText = takeOverlap(current.toString(), overlap);
        current.setLength(0);
        current.append(prefix);
        if (!overlapText.isEmpty() && prefix.length() + overlapText.length() < maxChars) {
            current.append(overlapText).append('\n');
        }
        current.append(text);
    }

    /** 超长表格保留表头后按行切片；超长代码块按行切片并重复围栏。 */
    private static List<String> splitAtomic(DocumentBlock block, String prefix, int maxChars) {
        String[] lines = block.text().split("\n", -1);
        List<String> pieces = new ArrayList<>();
        int budget = Math.max(100, maxChars - prefix.length());

        if (block.type() == DocumentBlock.Type.TABLE && lines.length > 2) {
            String header = lines[0] + "\n" + lines[1] + "\n";
            StringBuilder piece = new StringBuilder(header);
            for (int i = 2; i < lines.length; i++) {
                if (piece.length() + lines[i].length() + 1 > budget) {
                    pieces.add(piece.toString());
                    piece = new StringBuilder(header);
                }
                piece.append(lines[i]).append('\n');
            }
            pieces.add(piece.toString());
        } else {
            String lang = block.type() == DocumentBlock.Type.CODE ? block.meta() : "";
            StringBuilder piece = new StringBuilder();
            for (String line : lines) {
                if (piece.length() + line.length() + 1 > budget) {
                    pieces.add(DocumentTextRenderer.renderBlock(DocumentBlock.code(piece.toString(), lang)));
                    piece.setLength(0);
                }
                piece.append(line).append('\n');
            }
            pieces.add(DocumentTextRenderer.renderBlock(DocumentBlock.code(piece.toString(), lang)));
        }
        return pieces.stream().map(piece -> prefix + piece.trim()).toList();
    }

    private static String headingPrefix(Deque<DocumentBlock> headings) {
        if (headings.isEmpty()) {
            return "";
        }
        List<String> path = new ArrayList<>();
        for (DocumentBlock heading : headings) {
            path.add(heading.text());
        }
        int from = Math.max(0, path.size() - MAX_HEADING_DEPTH);
        return String.join(" > ", path.subList(from, path.size())) + "\n";
    }

    private static void flush(StringBuilder current, List<String> chunks) {
        String text = current.toString().trim();
        if (!text.isEmpty()) {
            chunks.add(text);
        }
        current.setLength(0);
    }

    private static int normalizeMax(int maxChars) {
        return maxChars < 100 ? DEFAULT_MAX_CHARS : maxChars;
    }

    private static int normalizeOverlap(int overlap, int maxChars) {
        return (overlap < 0 || overlap >= maxChars) ? Math.min(DEFAULT_OVERLAP, maxChars / 5) : overlap;
    }

    // ---------- 通用工具 ----------

    private static List<String> splitParagraphs(String text) {
        String[] raw = text.split("\\n\\s*\\n+");
        List<String> list = new ArrayList<>();
        for (String item : raw) {
            String t = item.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        if (list.isEmpty() && !text.isBlank()) {
            list.add(text);
        }
        return list;
    }

    private static List<String> splitLongText(String text, int maxChars, int overlap) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                int breakAt = findBreak(text, start, end);
                if (breakAt > start + maxChars / 3) {
                    end = breakAt;
                }
            }
            String part = text.substring(start, end).trim();
            if (!part.isEmpty()) {
                parts.add(part);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return parts;
    }

    private static int findBreak(String text, int start, int end) {
        for (int i = end; i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '\n' || c == '.' || c == '!' || c == '?') {
                return i;
            }
        }
        for (int i = end; i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '；' || c == ';' || c == '，' || c == ',' || c == ' ') {
                return i;
            }
        }
        return end;
    }

    private static String takeOverlap(String text, int overlap) {
        if (text == null || text.isEmpty() || overlap <= 0) {
            return "";
        }
        if (text.length() <= overlap) {
            return text;
        }
        return text.substring(text.length() - overlap);
    }
}
