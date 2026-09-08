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

    @Test
    @DisplayName("含中文标点 → 中文语境")
    void chinesePunctuationIsChinese() {
        assertTrue(CjkContextDetector.isChineseContext("，"));
        assertTrue(CjkContextDetector.isChineseContext("。"));
        assertTrue(CjkContextDetector.isChineseContext("hello，"));
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
}
