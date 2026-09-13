package com.aijob.server.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown → 纯文本（面向 RAG Embedding）。
 * <p>
 * 保留：标题文字、正文、代码块正文、列表/表格文字。<br>
 * 去掉：YAML frontmatter、链接/图片 URL、代码围栏与语言标记、行首 #。
 */
public final class MdTextExtractor {

    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\r?\\n.*?\\r?\\n---\\r?\\n?",
            Pattern.DOTALL
    );

    private static final Pattern CODE_FENCE = Pattern.compile(
            "```[^\\n]*\\r?\\n(.*?)```",
            Pattern.DOTALL
    );

    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");

    private static final Pattern LINK = Pattern.compile("(?<!!)\\[([^\\]]+)]\\([^)]*\\)");

    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})[ \\t]+(.+)$");

    private MdTextExtractor() {
    }

    public static String extract(Path filePath) {
        return extractDetailed(filePath).text();
    }

    public static DocumentExtractResult extractDetailed(Path filePath) {
        String raw = readUtf8Strict(filePath);
        String cleaned = clean(raw);
        return new DocumentExtractResult(cleaned, null);
    }

    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        Matcher fm = FRONTMATTER.matcher(text);
        if (fm.find() && fm.start() == 0) {
            text = text.substring(fm.end());
        }

        // 先抽出代码块正文，避免后续规则误伤代码
        StringBuffer codeBuf = new StringBuffer();
        Matcher codeMatcher = CODE_FENCE.matcher(text);
        while (codeMatcher.find()) {
            String body = codeMatcher.group(1) == null ? "" : codeMatcher.group(1);
            // 去掉末尾多余换行，保留内部内容
            if (body.endsWith("\r\n")) {
                body = body.substring(0, body.length() - 2);
            } else if (body.endsWith("\n")) {
                body = body.substring(0, body.length() - 1);
            }
            codeMatcher.appendReplacement(codeBuf, Matcher.quoteReplacement(body));
        }
        codeMatcher.appendTail(codeBuf);
        text = codeBuf.toString();

        // 图片：有 alt 保留 alt；无 alt 整段删除
        StringBuffer imgBuf = new StringBuffer();
        Matcher imgMatcher = IMAGE.matcher(text);
        while (imgMatcher.find()) {
            String alt = imgMatcher.group(1) == null ? "" : imgMatcher.group(1).trim();
            imgMatcher.appendReplacement(imgBuf, Matcher.quoteReplacement(alt));
        }
        imgMatcher.appendTail(imgBuf);
        text = imgBuf.toString();

        // 链接：保留锚文本
        StringBuffer linkBuf = new StringBuffer();
        Matcher linkMatcher = LINK.matcher(text);
        while (linkMatcher.find()) {
            String label = linkMatcher.group(1) == null ? "" : linkMatcher.group(1);
            linkMatcher.appendReplacement(linkBuf, Matcher.quoteReplacement(label));
        }
        linkMatcher.appendTail(linkBuf);
        text = linkBuf.toString();

        // 标题：只去掉行首 #，保留标题文字
        StringBuffer headingBuf = new StringBuffer();
        Matcher headingMatcher = HEADING.matcher(text);
        while (headingMatcher.find()) {
            String title = headingMatcher.group(2) == null ? "" : headingMatcher.group(2).trim();
            headingMatcher.appendReplacement(headingBuf, Matcher.quoteReplacement(title));
        }
        headingMatcher.appendTail(headingBuf);
        text = headingBuf.toString();

        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static String readUtf8Strict(Path filePath) {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new RuntimeException("Markdown 不是合法 UTF-8 编码", e);
        } catch (IOException e) {
            throw new RuntimeException("Markdown 文件读取失败", e);
        }
    }
}
