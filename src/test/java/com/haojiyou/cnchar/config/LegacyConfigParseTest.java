package com.haojiyou.cnchar.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 阶段0 特征化测试：精确复刻 {@code ReplaceCharConfig.reload()}（源码 :32-41）的
 * “换行成对串 → Map” 解析算法，并对 {@code CnCharSettingComponent.DEFAULT_STRING}（:18）的解析结果断言。
 *
 * <p>不直接调用 {@code reload()}：它依赖 {@code PropertiesComponent}（需平台 Application）。
 * 这里在测试内复刻同样的循环算法，输入用与生产常量逐字符一致的 {@link #DEFAULT_STRING}，
 * 保证纯 JUnit5 / 零平台依赖。
 *
 * <p>如实记录现状，<b>包括已知 bug</b>：默认表末尾 {@code 、 → /}（语义上本应为 {@code 、 → ,}）。
 * 后续阶段修正该映射时，本断言将作为“有意的行为变更”被翻转。
 */
class LegacyConfigParseTest {

    /**
     * 与 CnCharSettingComponent.DEFAULT_STRING（:18）逐字符一致：
     * 空格分隔的成对串，随后 " " → "\n"。中文/花引号用 Unicode 转义确保精确。
     * 顺序：， 。 ： ； ！ ？ “ ” ‘ ’ 【 】 （ ） 「 」 《 》 、
     */
    private static final String DEFAULT_STRING =
            ("\uFF0C , \u3002 . \uFF1A : \uFF1B ; \uFF01 ! \uFF1F ? "
                    + "\u201C \" \u201D \" \u2018 ' \u2019 ' "
                    + "\u3010 [ \u3011 ] \uFF08 ( \uFF09 ) "
                    + "\u300C { \u300D } \u300A < \u300B > \u3001 /")
                    .replace(" ", "\n");

    /**
     * 复刻 ReplaceCharConfig.reload() 的核心循环（:35-37）：
     * <pre>
     * String[] configString = value.split("\n");
     * for (int i = 0; i &lt; configString.length / 2; i++) {
     *     map.put(configString[2*i].trim(), configString[2*i+1].trim());
     * }
     * </pre>
     */
    private static Map<String, String> parse(String value) {
        Map<String, String> map = new HashMap<>();
        String[] configString = value.split("\n");
        for (int i = 0; i < configString.length / 2; i++) {
            map.put(configString[2 * i].trim(), configString[2 * i + 1].trim());
        }
        return map;
    }

    @Test
    @DisplayName("默认串解析出 19 对映射")
    void parsesNineteenPairs() {
        Map<String, String> map = parse(DEFAULT_STRING);
        assertEquals(19, map.size(), "默认表应有 19 对（38 个 token / 2）");
    }

    @Test
    @DisplayName("中文标点 → 英文标点 的常规映射")
    void standardPunctuationMappings() {
        Map<String, String> map = parse(DEFAULT_STRING);
        assertEquals(",", map.get("\uFF0C")); // ，
        assertEquals(".", map.get("\u3002")); // 。
        assertEquals(":", map.get("\uFF1A")); // ：
        assertEquals(";", map.get("\uFF1B")); // ；
        assertEquals("!", map.get("\uFF01")); // ！
        assertEquals("?", map.get("\uFF1F")); // ？
    }

    @Test
    @DisplayName("引号映射：左右双引号 → \"，左右单引号 → '")
    void quoteMappings() {
        Map<String, String> map = parse(DEFAULT_STRING);
        assertEquals("\"", map.get("\u201C")); // “
        assertEquals("\"", map.get("\u201D")); // ”
        assertEquals("'", map.get("\u2018")); // ‘
        assertEquals("'", map.get("\u2019")); // ’
    }

    @Test
    @DisplayName("括号/书名号映射")
    void bracketMappings() {
        Map<String, String> map = parse(DEFAULT_STRING);
        assertEquals("[", map.get("\u3010")); // 【
        assertEquals("]", map.get("\u3011")); // 】
        assertEquals("(", map.get("\uFF08")); // （
        assertEquals(")", map.get("\uFF09")); // ）
        assertEquals("{", map.get("\u300C")); // 「
        assertEquals("}", map.get("\u300D")); // 」
        assertEquals("<", map.get("\u300A")); // 《
        assertEquals(">", map.get("\u300B")); // 》
    }

    @Test
    @DisplayName("现状 BUG 特征化：、 当前 → /（语义上本应为 ,）")
    void enumerationComma_currentlyMapsToSlash() {
        Map<String, String> map = parse(DEFAULT_STRING);
        // 如实锁定当前行为：、(U+3001) → "/"
        assertEquals("/", map.get("\u3001"), "现状：、 被映射为 /（bug）");
        // 并显式记录它没有被映射为语义正确的逗号
        assertEquals(false, ",".equals(map.get("\u3001")),
                "现状下 、 未映射为 ,（后续阶段修正时此断言与上一条将一同翻转）");
    }

    @Test
    @DisplayName("未登记的字符不在映射表中")
    void unregisteredCharAbsent() {
        Map<String, String> map = parse(DEFAULT_STRING);
        assertNull(map.get("\u4E2D")); // 中
        assertNull(map.get("a"));
    }

    @Test
    @DisplayName("奇数 token 时最后一项被丢弃（length/2 截断）")
    void oddTokenCountDropsLast() {
        // "a\nb\nc" → length=3, 循环 i<1 → 只取 (a,b)，c 被丢弃
        Map<String, String> map = parse("a\nb\nc");
        assertEquals(1, map.size());
        assertEquals("b", map.get("a"));
        assertNull(map.get("c"));
    }

    @Test
    @DisplayName("空串解析出空表")
    void emptyStringYieldsEmptyMap() {
        // "".split("\n") → [""], length=1, 循环 i<0 不执行
        assertEquals(0, parse("").size());
    }

    @Test
    @DisplayName("token 两端空白被 trim")
    void tokensAreTrimmed() {
        Map<String, String> map = parse("  \uFF0C  \n  ,  ");
        assertEquals(",", map.get("\uFF0C"));
    }
}
