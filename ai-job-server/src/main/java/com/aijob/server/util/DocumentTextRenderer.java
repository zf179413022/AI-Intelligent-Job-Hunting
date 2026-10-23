package com.aijob.server.util;

import java.util.List;

/**
 * 把 {@link DocumentBlock} 序列渲染为面向 LLM / RAG 的结构化 Markdown 文本。
 * <p>
 * 保留标题、表格、代码围栏、列表与脚注标记：这些结构对检索与生成都有语义价值，
 * 比压平成纯文本更利于后续分块与引用。
 */
public final class DocumentTextRenderer {

    private DocumentTextRenderer() {
    }

    public static String render(List<DocumentBlock> blocks) {
        StringBuilder out = new StringBuilder();
        for (DocumentBlock block : blocks) {
            if (block == null || block.isBlank()) {
                continue;
            }
            String rendered = renderBlock(block);
            if (rendered.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(rendered);
        }
        return out.toString().trim();
    }

    public static String renderBlock(DocumentBlock block) {
        return switch (block.type()) {
            case HEADING -> "#".repeat(Math.min(6, Math.max(1, block.level()))) + " " + block.text().trim();
            case TABLE -> block.text().trim();
            case CODE -> renderCode(block);
            case FOOTNOTE -> "[^" + block.meta() + "]: " + block.text().trim();
            case LIST_ITEM -> "- " + block.text().trim();
            case QUOTE -> "> " + block.text().trim();
            case PARAGRAPH -> block.text().trim();
        };
    }

    private static String renderCode(DocumentBlock block) {
        String lang = block.meta() == null ? "" : block.meta().trim();
        String body = block.text().endsWith("\n") ? block.text() : block.text() + "\n";
        return "```" + lang + "\n" + body + "```";
    }
}
