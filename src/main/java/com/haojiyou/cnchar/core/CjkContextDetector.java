package com.haojiyou.cnchar.core;

/**
 * 描述: 自适应区域的 CJK 语境启发式检测器（纯逻辑，无 IDE 依赖，可脱平台单测）。
 *
 * <p>当某区域模式为 ADAPTIVE 时，扫描光标<b>前</b>当前行/片段（回溯至行首或上限 16 字符）：
 * <ul>
 *   <li>含 CJK 表意文字（U+4E00–U+9FFF、U+3400–U+4DBF 等）或中文标点 → 中文语境 → <b>不替换</b></li>
 *   <li>全为 ASCII/英文或数字 → 英文语境 → <b>替换</b></li>
 *   <li>前文为空（行内尚未输入）→ 保守视为自然语言区 → <b>不替换</b></li>
 * </ul>
 *
 * <p>该启发式为尽力而为，UI 需注明"自适应为启发式判断"。
 *
 * @author : best.xu
 */
public final class CjkContextDetector {

    private CjkContextDetector() {
    }

    /**
     * 判断光标前文是否为中文语境。
     *
     * @param textBeforeCursor 光标前文本（允许为空串，不允许 null）
     * @return true = 中文语境（不替换）；false = 英文语境（替换）
     */
    public static boolean isChineseContext(CharSequence textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
            return true;
        }
        boolean hasCjk = false;
        boolean hasAsciiLetterOrDigit = false;
        for (int i = 0; i < textBeforeCursor.length(); i++) {
            char c = textBeforeCursor.charAt(i);
            if (isCjkIdeograph(c) || isChinesePunctuation(c)) {
                hasCjk = true;
                break;
            }
            if (isAsciiLetterOrDigit(c)) {
                hasAsciiLetterOrDigit = true;
            }
        }
        if (hasCjk) {
            return true;
        }
        if (hasAsciiLetterOrDigit) {
            return false;
        }
        return true;
    }

    /**
     * CJK 统一表意文字区段（基本区 + 扩展 A）。
     */
    private static boolean isCjkIdeograph(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3400 && c <= 0x4DBF);
    }

    /**
     * 常见中文标点（全角标点区 + 部分 CJK 符号）。
     */
    private static boolean isChinesePunctuation(char c) {
        return (c >= 0x3000 && c <= 0x303F)
                || (c >= 0xFF00 && c <= 0xFFEF)
                || (c >= 0x2000 && c <= 0x206F && isGeneralPunctuation(c))
                || c == 0x201C || c == 0x201D
                || c == 0x2018 || c == 0x2019
                || c == 0x2014 || c == 0x2026;
    }

    /**
     * 通用标点区中偏"中文常用"的子集（破折号、省略号、引号等）。
     */
    private static boolean isGeneralPunctuation(char c) {
        return c == 0x2014 || c == 0x2013 || c == 0x2026
                || c == 0x201C || c == 0x201D || c == 0x2018 || c == 0x2019
                || c == 0x2030 || c == 0x2032 || c == 0x2033;
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
