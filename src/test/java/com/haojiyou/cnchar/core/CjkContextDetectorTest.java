package com.haojiyou.cnchar.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段3 单测：CJK 语境启发式检测器（纯逻辑，脱平台）。
 */
class CjkContextDetectorTest {

    @Test
    @DisplayName("空前文 → 中文语境（保守不替换）")
    void emptyPrefixIsChinese() {
        assertTrue(CjkContextDetector.isChineseContext(""));
        assertTrue(CjkContextDetector.isChineseContext(null));
    }

    @Test
    @DisplayName("含 CJK 表意文字 → 中文语境")
    void cjkIdeographIsChinese() {
        assertTrue(CjkContextDetector.isChineseContext("你好"));
        assertTrue(CjkContextDetector.isChineseContext("hello中"));
        assertTrue(CjkContextDetector.isChineseContext("测试abc"));
    }

    /**
     * 语义契约：检测器收到的是 {@code EditorContext#extractTextBefore} 已剔除"刚键入字符"的窗口
     * （窗口终点 offset-1）。因此像 "hello，"（键入的标点恰为窗口末尾）这类窗口在生产路径中不会
     * 出现——若窗口末尾出现中文标点，那它必然是键入<b>之前</b>已存在的前文。
     * 单个标点（如 "，"、"。"）仍是合法窗口：例如前文已有 "，" 时再键入字母，窗口即 "，"
     * （该字母须为候选字符，管线才会构造窗口——非候选键会被前置过滤链拦截，根本到不了语境检测）。
     */
    @Test
    @DisplayName("含中文标点（键入字符已被上游剔除，标点均为既有前文）→ 中文语境")
    void chinesePunctuationIsChinese() {
        assertTrue(CjkContextDetector.isChineseContext("，"));
        assertTrue(CjkContextDetector.isChineseContext("。"));
        assertTrue(CjkContextDetector.isChineseContext("hello，世界"));
    }

    @Test
    @DisplayName("纯 ASCII 字母/数字 → 英文语境")
    void pureAsciiIsEnglish() {
        assertFalse(CjkContextDetector.isChineseContext("hello"));
        assertFalse(CjkContextDetector.isChineseContext("abc123"));
        assertFalse(CjkContextDetector.isChineseContext("test "));
    }

    @Test
    @DisplayName("纯空白/符号（无 CJK 无 ASCII 字母数字）→ 中文语境（保守）")
    void pureSymbolsDefaultToChinese() {
        assertTrue(CjkContextDetector.isChineseContext("   "));
        assertTrue(CjkContextDetector.isChineseContext("+-*/"));
    }

    // ==================================================================================
    // 空白/换行/控制字符的证据语义：不构成证据，也不使检测"有判据"
    //   · 纯空白/换行/控制窗口 → hasAnyEvidence=false（调用方走键入字符补判）
    //   · 含有效内容的窗口忽略空白后判定（"hello\n" → 英文，"你好 \n" → 中文）
    //   · 例外：全角空格 U+3000 属中文标点区段（0x3000–0x303F），仍作 CJK 证据（历史 P0 修复语义）
    // ==================================================================================

    @Test
    @DisplayName("纯空白/换行/控制窗口 → hasAnyEvidence=false（无判据，走键入字符补判）")
    void blankWindowHasNoEvidence() {
        assertFalse(CjkContextDetector.hasAnyEvidence("\n"));
        assertFalse(CjkContextDetector.hasAnyEvidence("\r\n"));
        assertFalse(CjkContextDetector.hasAnyEvidence("   "));
        assertFalse(CjkContextDetector.hasAnyEvidence("\t"));
        assertFalse(CjkContextDetector.hasAnyEvidence("  \t \r\n "));
        assertFalse(CjkContextDetector.hasAnyEvidence("\u0000\u001B"));
    }

    @Test
    @DisplayName("纯空白/换行窗口的 isChineseContext 仍保守为中文（与空窗口同等对待）")
    void blankWindowStillConservativeChinese() {
        assertTrue(CjkContextDetector.isChineseContext("\n"));
        assertTrue(CjkContextDetector.isChineseContext("\r\n"));
        assertTrue(CjkContextDetector.isChineseContext("  \t \r\n "));
    }

    @Test
    @DisplayName("空白不使有效内容失效：含英文/中文的窗口忽略空白后仍有判据")
    void whitespaceDoesNotVoidEvidence() {
        assertTrue(CjkContextDetector.hasAnyEvidence("hello\n"));
        assertTrue(CjkContextDetector.hasAnyEvidence("  hello  "));
        assertTrue(CjkContextDetector.hasAnyEvidence("\r\n 你好"));
        assertTrue(CjkContextDetector.hasAnyEvidence("你好 \n"));
    }

    @Test
    @DisplayName("英文语境忽略空白：\"hello\\n\" / \"  hello  \" → 英文语境")
    void englishContextIgnoresWhitespace() {
        assertFalse(CjkContextDetector.isChineseContext("hello\n"));
        assertFalse(CjkContextDetector.isChineseContext("  hello  "));
        assertFalse(CjkContextDetector.isChineseContext("hello \r\n world"));
    }

    @Test
    @DisplayName("中文语境忽略空白：\"你好 \\n\" / \"\\r\\n 你好\" → 中文语境")
    void chineseContextIgnoresWhitespace() {
        assertTrue(CjkContextDetector.isChineseContext("你好 \n"));
        assertTrue(CjkContextDetector.isChineseContext("\r\n 你好"));
    }

    /**
     * 语义契约（U+3000 保持现状）：全角空格 U+3000 位于中文标点区段（0x3000–0x303F），虽满足
     * {@link Character#isWhitespace}，但仍作 CJK 证据、判为中文语境——与历史 P0 修复（U+3000
     * 作为中文标点进入替换管线）方向一致：键入的 U+3000 走替换管线，而前文已存在的 U+3000
     * 表明语境偏中文，按中文语境保守处理（扫描顺序：CJK 标点先于空白跳过）。
     */
    @Test
    @DisplayName("U+3000 全角空格仍为 CJK 证据（中文标点区段语义，保持现状）")
    void ideographicSpaceRemainsCjkEvidence() {
        assertTrue(CjkContextDetector.hasAnyEvidence("\u3000"));
        assertTrue(CjkContextDetector.isChineseContext("\u3000"));
        assertTrue(CjkContextDetector.isChineseContext("hello\u3000"));
    }
}
