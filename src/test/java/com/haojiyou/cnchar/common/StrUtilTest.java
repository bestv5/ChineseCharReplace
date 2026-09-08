package com.haojiyou.cnchar.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段0 特征化测试：锁定 {@link StrUtil} 现有语义（含 null 安全）。
 * 纯 JDK / 零平台依赖。本测试如实记录当前行为，不修改任何生产代码。
 */
class StrUtilTest {

    // ---- equalsIgnoreCase ----

    @Test
    @DisplayName("equalsIgnoreCase: 两者均为 null 返回 true（a==b）")
    void equalsIgnoreCase_bothNull_true() {
        assertTrue(StrUtil.equalsIgnoreCase(null, null));
    }

    @Test
    @DisplayName("equalsIgnoreCase: 仅一方为 null 返回 false")
    void equalsIgnoreCase_oneNull_false() {
        assertFalse(StrUtil.equalsIgnoreCase("abc", null));
        assertFalse(StrUtil.equalsIgnoreCase(null, "abc"));
    }

    @Test
    @DisplayName("equalsIgnoreCase: 忽略大小写相等")
    void equalsIgnoreCase_caseInsensitive() {
        assertTrue(StrUtil.equalsIgnoreCase("abc", "ABC"));
        assertTrue(StrUtil.equalsIgnoreCase("Java", "java"));
        assertTrue(StrUtil.equalsIgnoreCase("", ""));
    }

    @Test
    @DisplayName("equalsIgnoreCase: 内容不同返回 false")
    void equalsIgnoreCase_different_false() {
        assertFalse(StrUtil.equalsIgnoreCase("abc", "abd"));
        assertFalse(StrUtil.equalsIgnoreCase("abc", "ab"));
    }

    @Test
    @DisplayName("equalsIgnoreCase: 支持任意 CharSequence（非 String）")
    void equalsIgnoreCase_charSequence() {
        assertTrue(StrUtil.equalsIgnoreCase(new StringBuilder("XML"), new StringBuffer("xml")));
    }

    // ---- containsAny ----

    @Test
    @DisplayName("containsAny: cs 为 null 返回 false")
    void containsAny_nullCs_false() {
        assertFalse(StrUtil.containsAny(null, "a"));
    }

    @Test
    @DisplayName("containsAny: searches 为 null 返回 false")
    void containsAny_nullSearches_false() {
        assertFalse(StrUtil.containsAny("hello", (CharSequence[]) null));
    }

    @Test
    @DisplayName("containsAny: 命中任一子串返回 true")
    void containsAny_hit_true() {
        assertTrue(StrUtil.containsAny("hello", "ell"));
        assertTrue(StrUtil.containsAny("hello", "xyz", "ell"));
    }

    @Test
    @DisplayName("containsAny: 未命中返回 false")
    void containsAny_miss_false() {
        assertFalse(StrUtil.containsAny("hello", "xyz"));
    }

    @Test
    @DisplayName("containsAny: searches 中的 null 元素被跳过")
    void containsAny_nullElementSkipped() {
        assertTrue(StrUtil.containsAny("hello", null, "ell"));
        assertFalse(StrUtil.containsAny("hello", null, "xyz"));
    }

    // ---- startsWithAny ----

    @Test
    @DisplayName("startsWithAny: cs 为 null 返回 false")
    void startsWithAny_nullCs_false() {
        assertFalse(StrUtil.startsWithAny(null, "a"));
    }

    @Test
    @DisplayName("startsWithAny: prefixes 为 null 返回 false")
    void startsWithAny_nullPrefixes_false() {
        assertFalse(StrUtil.startsWithAny("hello", (CharSequence[]) null));
    }

    @Test
    @DisplayName("startsWithAny: 命中前缀返回 true")
    void startsWithAny_hit_true() {
        assertTrue(StrUtil.startsWithAny("hello", "he"));
        assertTrue(StrUtil.startsWithAny("hello", "xyz", "he"));
    }

    @Test
    @DisplayName("startsWithAny: 非前缀返回 false")
    void startsWithAny_miss_false() {
        assertFalse(StrUtil.startsWithAny("hello", "lo"));
    }

    // ---- endsWithAny ----

    @Test
    @DisplayName("endsWithAny: cs 为 null 返回 false")
    void endsWithAny_nullCs_false() {
        assertFalse(StrUtil.endsWithAny(null, "a"));
    }

    @Test
    @DisplayName("endsWithAny: suffixes 为 null 返回 false")
    void endsWithAny_nullSuffixes_false() {
        assertFalse(StrUtil.endsWithAny("hello", (CharSequence[]) null));
    }

    @Test
    @DisplayName("endsWithAny: 命中后缀返回 true")
    void endsWithAny_hit_true() {
        assertTrue(StrUtil.endsWithAny("hello", "lo"));
        assertTrue(StrUtil.endsWithAny("hello", "xyz", "lo"));
    }

    @Test
    @DisplayName("endsWithAny: 非后缀返回 false")
    void endsWithAny_miss_false() {
        assertFalse(StrUtil.endsWithAny("hello", "he"));
    }

    // ---- isBlank ----

    @Test
    @DisplayName("isBlank: null 返回 true")
    void isBlank_null_true() {
        assertTrue(StrUtil.isBlank(null));
    }

    @Test
    @DisplayName("isBlank: 空串返回 true")
    void isBlank_empty_true() {
        assertTrue(StrUtil.isBlank(""));
    }

    @Test
    @DisplayName("isBlank: 全空白（空格/制表符/换行）返回 true")
    void isBlank_whitespaceOnly_true() {
        assertTrue(StrUtil.isBlank("   "));
        assertTrue(StrUtil.isBlank("\t\n"));
    }

    @Test
    @DisplayName("isBlank: 含非空白字符返回 false")
    void isBlank_nonBlank_false() {
        assertFalse(StrUtil.isBlank(" a "));
        assertFalse(StrUtil.isBlank("a"));
    }

    // ---- isNotBlank ----

    @Test
    @DisplayName("isNotBlank: 与 isBlank 互补")
    void isNotBlank_complementary() {
        assertFalse(StrUtil.isNotBlank(null));
        assertFalse(StrUtil.isNotBlank(""));
        assertFalse(StrUtil.isNotBlank("   "));
        assertFalse(StrUtil.isNotBlank("\t\n"));
        assertTrue(StrUtil.isNotBlank(" a "));
        assertTrue(StrUtil.isNotBlank("a"));
    }

    // ---- trim ----

    @Test
    @DisplayName("trim: null 返回 null")
    void trim_null_null() {
        assertNull(StrUtil.trim(null));
    }

    @Test
    @DisplayName("trim: 去除首尾空白")
    void trim_stripsWhitespace() {
        assertEquals("a", StrUtil.trim("  a  "));
        assertEquals("a", StrUtil.trim("a"));
    }

    @Test
    @DisplayName("trim: 空串与全空白返回空串")
    void trim_blankToEmpty() {
        assertEquals("", StrUtil.trim(""));
        assertEquals("", StrUtil.trim("   "));
    }
}
