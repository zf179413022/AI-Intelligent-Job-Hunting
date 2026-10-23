package com.aijob.server.util;

/**
 * 文档结构化块：解析器的中间表示，供渲染、分块与统计复用。
 * <p>
 * text 的形态由 type 决定：
 * <ul>
 *   <li>TABLE：规范化 Markdown 表格（含表头与分隔行）</li>
 *   <li>CODE：代码正文（不含围栏），meta 为语言标记</li>
 *   <li>FOOTNOTE：脚注正文，meta 为脚注 id</li>
 *   <li>HEADING：标题文字，level 为 1~6</li>
 *   <li>其余：纯文本</li>
 * </ul>
 */
public record DocumentBlock(Type type, String text, int level, String meta) {

    public enum Type {
        HEADING, PARAGRAPH, LIST_ITEM, TABLE, CODE, FOOTNOTE, QUOTE
    }

    public DocumentBlock {
        text = text == null ? "" : text;
        meta = meta == null ? "" : meta;
    }

    public static DocumentBlock heading(int level, String text) {
        return new DocumentBlock(Type.HEADING, text, level, "");
    }

    public static DocumentBlock paragraph(String text) {
        return new DocumentBlock(Type.PARAGRAPH, text, 0, "");
    }

    public static DocumentBlock listItem(String text) {
        return new DocumentBlock(Type.LIST_ITEM, text, 0, "");
    }

    public static DocumentBlock table(String markdown) {
        return new DocumentBlock(Type.TABLE, markdown, 0, "");
    }

    public static DocumentBlock code(String body, String lang) {
        return new DocumentBlock(Type.CODE, body, 0, lang);
    }

    public static DocumentBlock footnote(String id, String body) {
        return new DocumentBlock(Type.FOOTNOTE, body, 0, id);
    }

    public static DocumentBlock quote(String text) {
        return new DocumentBlock(Type.QUOTE, text, 0, "");
    }

    /** 是否必须作为整体进入同一 chunk（表格/代码语义不可拆分）。 */
    public boolean isAtomic() {
        return type == Type.TABLE || type == Type.CODE;
    }

    public boolean isBlank() {
        return text.isBlank();
    }
}
