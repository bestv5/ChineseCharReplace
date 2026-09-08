package com.haojiyou.cnchar.convert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 阶段2 单测：三层转换引擎 {@link CharConverter}（纯 JUnit5、脱平台）。
 *
 * <p>覆盖：层3 全角偏移、层2 精选 CJK 表（含 {@code 、→,} 修正与 {@code —→-} §38 权威决策）、
 * 一对多（{@code …→...}）、{@code U+3000→空格}、层1 自定义表优先级、非候选字符返回 null 哨兵、
 * 多字符尾部兜底与 {@link CharConverter#MAX_MULTI_CHAR_LEN} 边界。
 *
 * <p>CJK 字符一律用 Unicode 转义书写，规避源码编码差异。
 */
class CharConverterTest {

    private static CharConverter emptyCustom() {
        return new CharConverter(Collections.emptyMap());
    }

    private static CharConverter withCustom(Map<String, String> custom) {
        return new CharConverter(custom);
    }

    // ---- 层3：全角偏移 ----

    @Test
    @DisplayName("层3 全角标点 → ASCII（−0xFEE0 偏移）")
    void fullWidthPunctuationToAscii() {
        CharConverter c = emptyCustom();
        assertEquals(",", c.convertChar('\uFF0C').text); // ，
        assertEquals(":", c.convertChar('\uFF1A').text); // ：
        assertEquals(";", c.convertChar('\uFF1B').text); // ；
        assertEquals("!", c.convertChar('\uFF01').text); // ！
        assertEquals("?", c.convertChar('\uFF1F').text); // ？
        assertEquals("(", c.convertChar('\uFF08').text); // （
        assertEquals(")", c.convertChar('\uFF09').text); // ）
        // 等长替换：delta == 0
        assertEquals(0, c.convertChar('\uFF0C').delta);
    }

    @Test
    @DisplayName("层3 全角字母/数字 → ASCII")
    void fullWidthLettersAndDigits() {
        CharConverter c = emptyCustom();
        assertEquals("A", c.convertChar('\uFF21').text); // Ａ
        assertEquals("a", c.convertChar('\uFF41').text); // ａ
        assertEquals("0", c.convertChar('\uFF10').text); // ０
        assertEquals("Z", c.convertChar('\uFF3A').text); // Ｚ
    }

    @Test
    @DisplayName("层3 表意空格 U+3000 → 半角空格")
    void ideographicSpaceToAsciiSpace() {
        CharConverter c = emptyCustom();
        CharConverter.Conversion r = c.convertChar('\u3000');
        assertNotNull(r);
        assertEquals(" ", r.text);
        assertEquals(0, r.delta);
    }

    // ---- 层2：精选 CJK 标点表 ----

    @Test
    @DisplayName("层2 精选表：。→.")
    void curatedPeriod() {
        assertEquals(".", emptyCustom().convertChar('\u3002').text); // 。
    }

    @Test
    @DisplayName("层2 修正现状 BUG：、→,（不再是 /）")
    void enumerationCommaFixedToComma() {
        CharConverter.Conversion r = emptyCustom().convertChar('\u3001'); // 、
        assertNotNull(r);
        assertEquals(",", r.text, "、 应映射为半角逗号");
        // 显式断言不再是现状的 "/"
        assertEquals(false, "/".equals(r.text), "、 不应再映射为 /");
    }

    @Test
    @DisplayName("层2 书名号/尖括号：《》〈〉 → <>")
    void bookTitleAndAngleBrackets() {
        CharConverter c = emptyCustom();
        assertEquals("<", c.convertChar('\u300A').text); // 《
        assertEquals(">", c.convertChar('\u300B').text); // 》
        assertEquals("<", c.convertChar('\u3008').text); // 〈
        assertEquals(">", c.convertChar('\u3009').text); // 〉
    }

    @Test
    @DisplayName("层2 各类括号：【】「」〔〕→[]，『』→\"")
    void bracketFamily() {
        CharConverter c = emptyCustom();
        assertEquals("[", c.convertChar('\u3010').text); // 【
        assertEquals("]", c.convertChar('\u3011').text); // 】
        assertEquals("[", c.convertChar('\u300C').text); // 「（新默认 [，非旧 {）
        assertEquals("]", c.convertChar('\u300D').text); // 」
        assertEquals("[", c.convertChar('\u3014').text); // 〔
        assertEquals("]", c.convertChar('\u3015').text); // 〕
        assertEquals("\"", c.convertChar('\u300E').text); // 『
        assertEquals("\"", c.convertChar('\u300F').text); // 』
    }

    @Test
    @DisplayName("层2 花引号：\u201C\u201D→\"，\u2018\u2019→'")
    void curlyQuotes() {
        CharConverter c = emptyCustom();
        assertEquals("\"", c.convertChar('\u201C').text); // “
        assertEquals("\"", c.convertChar('\u201D').text); // ”
        assertEquals("'", c.convertChar('\u2018').text);  // ‘
        assertEquals("'", c.convertChar('\u2019').text);  // ’
    }

    @Test
    @DisplayName("层2 §38 权威：—→- 单字符（非 --）")
    void emDashIsSingleCharPerSpec38() {
        CharConverter.Conversion r = emptyCustom().convertChar('\u2014'); // —
        assertNotNull(r);
        assertEquals("-", r.text, "§38 权威默认表：— → 单个 -");
        assertEquals(1, r.length());
        assertEquals(0, r.delta);
        assertEquals(false, "--".equals(r.text), "如需 -- 应由用户自定义映射实现");
    }

    @Test
    @DisplayName("层2 一对多：…→... 且长度增量正确")
    void ellipsisOneToMany() {
        CharConverter.Conversion r = emptyCustom().convertChar('\u2026'); // …
        assertNotNull(r);
        assertEquals("...", r.text);
        assertEquals(3, r.length());
        assertEquals(2, r.delta, "3 − 1 = 2");
    }

    @Test
    @DisplayName("层2 其它：·→. ，〜→~")
    void middleDotAndWaveDash() {
        CharConverter c = emptyCustom();
        assertEquals(".", c.convertChar('\u00B7').text); // ·
        assertEquals("~", c.convertChar('\u301C').text); // 〜
    }

    // ---- 层1：自定义表优先级 ----

    @Test
    @DisplayName("层1 自定义覆盖层2 精选表（、 用户改为 /）")
    void customOverridesCurated() {
        Map<String, String> custom = new HashMap<>();
        custom.put("\u3001", "/"); // 、 → /（用户旧值，忠实保留）
        CharConverter.Conversion r = withCustom(custom).convertChar('\u3001');
        assertNotNull(r);
        assertEquals("/", r.text, "自定义表优先级最高");
    }

    @Test
    @DisplayName("层1 自定义覆盖：。→DOT")
    void customOverridesPeriod() {
        Map<String, String> custom = new HashMap<>();
        custom.put("\u3002", "DOT");
        assertEquals("DOT", withCustom(custom).convertChar('\u3002').text);
    }

    @Test
    @DisplayName("层1 自定义单字符映射 ASCII")
    void customSingleAscii() {
        Map<String, String> custom = new HashMap<>();
        custom.put("a", "b");
        assertEquals("b", withCustom(custom).convertChar('a').text);
    }

    // ---- 非候选：返回 null 哨兵 ----

    @Test
    @DisplayName("非候选字符返回 null（无异常）")
    void nonCandidateReturnsNull() {
        CharConverter c = emptyCustom();
        assertNull(c.convertChar('a'));
        assertNull(c.convertChar('1'));
        assertNull(c.convertChar(' '));
        assertNull(c.convertChar('\u4E2D')); // 中（CJK 表意，不转换）
    }

    // ---- 多字符尾部兜底 + MAX_MULTI_CHAR_LEN 边界 ----

    @Test
    @DisplayName("多字符尾部兜底：最长优先命中自定义多字符键")
    void multiCharTailMatch() {
        Map<String, String> custom = new HashMap<>();
        custom.put("abc", "XY");
        CharConverter.Conversion r = withCustom(custom).convertTail("zzabc");
        assertNotNull(r);
        assertEquals("XY", r.text);
        assertEquals(-1, r.delta, "2 − 3 = −1");
    }

    @Test
    @DisplayName("MAX_MULTI_CHAR_LEN 边界：长度 5 的多字符键可命中")
    void multiCharLenAtBoundaryMatches() {
        assertEquals(5, CharConverter.MAX_MULTI_CHAR_LEN, "取代旧魔数 >5，取值 5");
        Map<String, String> custom = new HashMap<>();
        custom.put("abcde", "OK5");
        CharConverter.Conversion r = withCustom(custom).convertTail("abcde");
        assertNotNull(r, "长度 5 的键在窗口内，应命中");
        assertEquals("OK5", r.text);
        assertEquals(-2, r.delta, "OK5 长度 3 − 键长度 5 = −2");
    }

    @Test
    @DisplayName("MAX_MULTI_CHAR_LEN 边界：长度 6 的多字符键不被匹配（超窗口）")
    void multiCharLenBeyondBoundaryIgnored() {
        Map<String, String> custom = new HashMap<>();
        custom.put("abcdef", "NO6"); // 长度 6 > MAX_MULTI_CHAR_LEN
        assertNull(withCustom(custom).convertTail("abcdef"), "窗口上限 5，长度 6 键不应命中");
    }

    @Test
    @DisplayName("多字符尾部兜底：null / 无命中返回 null")
    void multiCharTailNullSafe() {
        assertNull(emptyCustom().convertTail(null));
        assertNull(emptyCustom().convertTail("abc"));
    }
}
