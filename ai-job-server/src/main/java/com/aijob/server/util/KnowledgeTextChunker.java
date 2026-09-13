package com.aijob.server.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库文本分块：优先按段落，目标约 500～800 中文字符，overlap 约 80～120。
 */
public final class KnowledgeTextChunker {

    public static final int DEFAULT_MAX_CHARS = 700;
    public static final int DEFAULT_OVERLAP = 100;

    private KnowledgeTextChunker() {
    }

    public static List<String> chunk(String text) {
        return chunk(text, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP);
    }

    public static List<String> chunk(String text, int maxChars, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (maxChars < 100) {
            maxChars = DEFAULT_MAX_CHARS;
        }
        if (overlap < 0 || overlap >= maxChars) {
            overlap = Math.min(DEFAULT_OVERLAP, maxChars / 5);
        }

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
        // 尽量在句号/问号/换行处切断
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
