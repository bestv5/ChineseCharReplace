package com.haojiyou.cnchar.core;

/**
 * 描述: 自适应区域的 CJK 语境启发式检测器（纯逻辑，无 IDE 依赖，可脱平台单测）。
 *
 * <p>当某区域模式为 ADAPTIVE 时，扫描光标<b>前</b>当前行/片段（回溯至行首或上限 16 字符）：
 * <ul>
 *   <li>含 CJK 表意文字（U+4E00–U+9FFF、U+3400–U+4DBF 等）或中文标点 → 中文语境 → <b>不替换</b></li>
 *   <li>全为 ASCII/英文或数字 → 英文语境 → <b>替换</b></li>
 *   <li>空白字符（空格、\t、\r、\n 等）与控制字符不构成任何证据，也不使检测"有判据"：
 *       纯空白/换行/控制窗口等同无判据（走键入字符补判），含有效内容的窗口忽略空白后判定；
 *       例外：全角空格 U+3000 属中文标点区段（0x3000–0x303F），仍作 CJK 证据（历史 P0 修复语义）</li>
 *   <li>前文为空（行内尚未输入）或无判据（无 CJK 也无 ASCII 字母数字）→ 保守视为中文语境 → <b>不替换</b>；
 *       调用方（PolicyEngine 的 ADAPTIVE 分支）应先以 {@link #hasAnyEvidence} 探测判据，
 *       无判据时改以键入字符自身补判，避免 ASCII 自定义键在行首键入被静默跳过</li>
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
     * <p>判定证据时跳过空白字符（{@link Character#isWhitespace}：空格、\t、\r、\n 等）与控制字符
     * （{@link Character#isISOControl}）——空白与控制字符不作为语境证据：仅由空白/换行/控制字符
     * 组成的窗口与空窗口同等对待（保守中文语境），含有效内容的窗口忽略空白后按有效内容判定。
     * 例外：全角空格 U+3000 位于中文标点区段（0x3000–0x303F），先于空白跳过被识别为 CJK 证据，
     * 中文语境语义保持不变（历史 P0 修复语义）。
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
            // 先按 CJK 判定：U+3000 等中文标点区字符虽满足 isWhitespace，但须保持 CJK 证据语义，
            // 不能被下方的空白跳过吞掉（判定顺序不可调换）
            if (isCjkIdeograph(c) || isChinesePunctuation(c)) {
                hasCjk = true;
                break;
            }
            // 空白与控制字符不作为语境证据：跳过，不参与中英文判定
            if (isSkippableChar(c)) {
                continue;
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
     * 判断窗口是否含有可供语境判定的"证据"：存在 CJK 相关字符（表意文字或中文标点）或 ASCII 字母/数字。
     *
     * <p>窗口为空、或仅含空白/符号（无任何 CJK 也无 ASCII 字母数字）时，{@link #isChineseContext}
     * 缺乏判据，只能保守按中文语境处理。此类场景下调用方应以键入字符自身作为唯一证据补判——
     * 语境窗口终点已剔除刚键入字符，行首键入时窗口为空，此时键入字符是唯一的语境线索：
     * 若直接交给保守逻辑，ASCII 自定义键（如配置 {@code "d"→"的"}）在行首键入会被静默跳过，
     * 相对旧行为（窗口含键入字符）发生静默退化。
     *
     * <p>空白与控制字符（{@link Character#isWhitespace} / {@link Character#isISOControl}）一律
     * 跳过、不构成证据：仅含空白/换行/控制字符的窗口返回 false（如 "\n"、"   "、"\t"）；
     * 含有效内容的窗口忽略空白后判定（如 "hello\n"、"  hello " 仍有判据）。
     * 例外：全角空格 U+3000 属中文标点区段（0x3000–0x303F），仍作 CJK 证据。
     *
     * @param textBeforeCursor 光标前文本（允许为空串，不允许 null）
     * @return true = 窗口内存在 CJK 字符或 ASCII 字母/数字，检测器有判据；false = 无判据
     */
    public static boolean hasAnyEvidence(CharSequence textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
            return false;
        }
        for (int i = 0; i < textBeforeCursor.length(); i++) {
            char c = textBeforeCursor.charAt(i);
            // 先按 CJK 判定：U+3000 虽满足 isWhitespace，但保持 CJK 证据语义（判定顺序不可调换）
            if (isCjkIdeograph(c) || isChinesePunctuation(c)) {
                return true;
            }
            // 空白与控制字符不作为语境证据：跳过
            if (isSkippableChar(c)) {
                continue;
            }
            if (isAsciiLetterOrDigit(c)) {
                return true;
            }
        }
        return false;
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

    /**
     * 空白字符（空格、\t、\r、\n 等，{@link Character#isWhitespace}）与 ISO 控制字符
     * （{@link Character#isISOControl}）：不作为语境证据。
     *
     * <p>注意：全角空格 U+3000 虽满足 {@link Character#isWhitespace}，但两个调用点均先按中文标点
     * （0x3000–0x303F 区段）识别为 CJK 证据后才会进入本判定，其"中文语境证据"语义不受影响。
     */
    private static boolean isSkippableChar(char c) {
        return Character.isWhitespace(c) || Character.isISOControl(c);
    }
}
