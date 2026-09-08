package com.haojiyou.cnchar.convert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 描述: 三层字符转换引擎（性能关键，纯逻辑、不含 Editor/Document，可脱离 IDE 单测）。
 *
 * <p>优先级由高到低：
 * <ol>
 *   <li><b>层1 自定义表</b>：来自快照的 {@code customMappings}（{@code Map<String,String>}），
 *       支持单字符与多字符键；多字符键用于“光标前尾部兜底匹配”，受 {@link #MAX_MULTI_CHAR_LEN} 约束。</li>
 *   <li><b>层2 精选 CJK 标点表</b>：表驱动（~16 项），修正现状 bug（{@code 、→,}），
 *       含一对多 {@code …→...}；以 spec §38 权威默认表为准（{@code —→-} 单字符）。</li>
 *   <li><b>层3 全角偏移</b>：{@code if(c>=0xFF01 && c<=0xFF5E) return (char)(c-0xFEE0);}
 *       （覆盖全角标点 / 字母 / 数字）；表意空格 {@code U+3000→' '}。<b>算术运算，不入表</b>。</li>
 * </ol>
 *
 * <p><b>性能约定</b>：O(1)/字符；<b>热路径不使用 {@code java.text.Normalizer}</b>，绝不做整行/整文档 NFKC。
 * {@link #convertChar(char)} 对非候选字符返回 {@code null} 哨兵。真正的“零分配 O(1) 候选门控”由
 * {@code CharAutoReplaceSettings.isCandidateChar(char)}（volatile 快照 + 位图探测）承担，
 * 本转换器只在门控通过后（候选路径，罕见）被调用，故候选路径上的少量分配可接受。
 *
 * @author : best.xu
 */
public final class CharConverter {

    /**
     * 多字符自定义键“光标前尾部兜底匹配”的最大长度。
     *
     * <p>来源：取代旧 {@code CharTypedDocumentLisener.documentChanged} 中的魔数判断
     * {@code event.getNewLength() > 5}（插入片段长度 &gt; 5 即不处理）。因此尾部多字符匹配的
     * 窗口上限沿用 5，保持与旧行为一致、且避免无界的回溯匹配开销。
     */
    public static final int MAX_MULTI_CHAR_LEN = 5;

    /** 全角 ASCII 变体区起点 {@code U+FF01}（！）。 */
    private static final char FULLWIDTH_START = 0xFF01;
    /** 全角 ASCII 变体区终点 {@code U+FF5E}（～）。 */
    private static final char FULLWIDTH_END = 0xFF5E;
    /** 全角 → 半角偏移量：{@code 0xFF01 - 0xFEE0 == 0x0021}。 */
    private static final int FULLWIDTH_OFFSET = 0xFEE0;
    /** 表意空格 {@code U+3000}。 */
    private static final char IDEOGRAPHIC_SPACE = 0x3000;

    /**
     * 层2 精选 CJK 标点表（spec §38 权威默认表）。键为单个 CJK 标点，值为半角替换串（可一对多）。
     * 使用 Unicode 转义书写，规避源码文件编码差异；每项后附实际字符注释。
     */
    private static final Map<Character, String> CURATED;

    static {
        Map<Character, String> m = new HashMap<>();
        m.put('\u3002', ".");    // 。 → .
        m.put('\u3001', ",");    // 、 → ,  （修正现状误映射 "/"）
        m.put('\u300A', "<");    // 《 → <
        m.put('\u300B', ">");    // 》 → >
        m.put('\u3008', "<");    // 〈 → <
        m.put('\u3009', ">");    // 〉 → >
        m.put('\u3010', "[");    // 【 → [
        m.put('\u3011', "]");    // 】 → ]
        m.put('\u300C', "[");    // 「 → [
        m.put('\u300D', "]");    // 」 → ]
        m.put('\u300E', "\"");   // 『 → "
        m.put('\u300F', "\"");   // 』 → "
        m.put('\u3014', "[");    // 〔 → [
        m.put('\u3015', "]");    // 〕 → ]
        m.put('\u201C', "\"");   // “ → "
        m.put('\u201D', "\"");   // ” → "
        m.put('\u2018', "'");    // ‘ → '
        m.put('\u2019', "'");    // ’ → '
        m.put('\u2014', "-");    // — → -   （§38 权威：单字符；如需 "--" 请用自定义映射）
        m.put('\u2026', "...");  // … → ... （一对多，长度变化）
        m.put('\u00B7', ".");    // · → .
        m.put('\u301C', "~");    // 〜 → ~
        CURATED = Collections.unmodifiableMap(m);
    }

    /** 层1 自定义表（构造入参 = 映射快照，单/多字符键，最高优先级）。 */
    private final Map<String, String> custom;

    /**
     * @param customMappings 自定义映射快照（可为 null，视为空表）。此表已由
     *                       {@code CharAutoReplaceSettings.Snapshot} 冻结，逻辑上不可变。
     */
    public CharConverter(Map<String, String> customMappings) {
        this.custom = customMappings == null ? Collections.emptyMap() : customMappings;
    }

    /**
     * 暴露精选 CJK 标点表（只读），供快照构建候选位图 / 合并 charMap 使用。
     */
    public static Map<Character, String> curatedTable() {
        return CURATED;
    }

    /**
     * 全角区起点（含），供快照构建候选位图使用。
     */
    public static char fullWidthStart() {
        return FULLWIDTH_START;
    }

    /**
     * 全角区终点（含）。
     */
    public static char fullWidthEnd() {
        return FULLWIDTH_END;
    }

    /**
     * 全角 → 半角偏移量。
     */
    public static int fullWidthOffset() {
        return FULLWIDTH_OFFSET;
    }

    /**
     * 表意空格 {@code U+3000}。
     */
    public static char ideographicSpace() {
        return IDEOGRAPHIC_SPACE;
    }

    /**
     * 单字符三层转换。
     *
     * @param c 输入字符（应先经 {@code isCandidateChar} 门控；此处对非候选字符安全返回 null）
     * @return 命中则返回携带“替换串 + 长度增量 delta”的不可变结果；<b>非候选 / 不可转换返回 {@code null}</b>
     */
    public Conversion convertChar(char c) {
        // 层1 自定义表（单字符键，最高优先）
        String r = custom.get(String.valueOf(c));
        if (r != null) {
            return new Conversion(r, r.length() - 1);
        }
        // 层2 精选 CJK 标点表
        r = CURATED.get(c);
        if (r != null) {
            return new Conversion(r, r.length() - 1);
        }
        // 层3 全角偏移（算术，无表）
        if (c == IDEOGRAPHIC_SPACE) {
            return new Conversion(" ", 0);
        }
        if (c >= FULLWIDTH_START && c <= FULLWIDTH_END) {
            return new Conversion(String.valueOf((char) (c - FULLWIDTH_OFFSET)), 0);
        }
        return null;
    }

    /**
     * 多字符尾部兜底匹配：在光标前文本 {@code beforeCursor} 的<b>尾部</b>，按“最长优先”尝试匹配
     * 自定义多字符键（长度 {@code [2, MAX_MULTI_CHAR_LEN]}）。用于 IME / 组合输入产生的多字符片段。
     *
     * @param beforeCursor 光标前的文本（取其尾部窗口）
     * @return 命中最长多字符自定义键则返回结果（delta = 替换串长度 − 匹配到的键长度）；否则 {@code null}
     */
    public Conversion convertTail(CharSequence beforeCursor) {
        if (beforeCursor == null) {
            return null;
        }
        int len = beforeCursor.length();
        int max = Math.min(len, MAX_MULTI_CHAR_LEN);
        // 从最长窗口向短回溯，最长优先命中
        for (int size = max; size >= 2; size--) {
            String key = beforeCursor.subSequence(len - size, len).toString();
            String r = custom.get(key);
            if (r != null) {
                return new Conversion(r, r.length() - size);
            }
        }
        return null;
    }

    /**
     * 单字符 / 多字符转换的不可变结果。
     */
    public static final class Conversion {
        /** 替换后的字符串（一对多时长度 &gt; 1，如 {@code …→...}）。 */
        public final String text;
        /** 长度增量 = {@code text.length() − 被替换的原长度}，供光标重算。单字符替换一对多时 &gt; 0。 */
        public final int delta;

        public Conversion(String text, int delta) {
            this.text = text;
            this.delta = delta;
        }

        /** 替换串长度。 */
        public int length() {
            return text.length();
        }

        @Override
        public String toString() {
            return "Conversion{" + text + ", delta=" + delta + '}';
        }
    }
}
