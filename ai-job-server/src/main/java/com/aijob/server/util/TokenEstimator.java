package com.aijob.server.util;

/**
 * 轻量 token 估算：中文按 1 token/字，英文/数字按 4 字符/token，符号按 2 字符/token。
 * <p>
 * 只用于元数据展示与调参参考，不参与切分预算（切分仍按字符数，保证确定性）。
 * 字符数 ≠ token 数：同样 700 字符，中文约 700 token，代码约 250 token。
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double tokens = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                tokens += 1.0;
            } else if (Character.isWhitespace(c)) {
                tokens += 0.1;
            } else if (Character.isLetterOrDigit(c)) {
                tokens += 0.25;
            } else {
                tokens += 0.5;
            }
        }
        return Math.max(1, (int) Math.ceil(tokens));
    }

    private static boolean isCjk(char c) {
        return (c >= '\u4E00' && c <= '\u9FFF')
                || (c >= '\u3000' && c <= '\u303F')
                || (c >= '\uFF00' && c <= '\uFFEF');
    }
}
