package com.haojiyou.cnchar.common;

/**
 * 描述: 纯 JDK 实现的字符串工具，替代 commons-lang3 中被标记为 deprecated 的方法
 * （equalsIgnoreCase / containsAny / startsWithAny / endsWithAny）。
 * 仅使用 JDK String API，兼容 since-build 203 及以后所有版本，且不会触发插件验证器的 deprecated 告警。
 * 语义与 commons-lang3 StringUtils 对应方法保持一致（null 安全）。
 *
 * @author : best.xu
 */
public final class StrUtil {

    private StrUtil() {
    }

    /**
     * null 安全的忽略大小写比较。两者均为 null 返回 true，仅一方为 null 返回 false。
     */
    public static boolean equalsIgnoreCase(CharSequence a, CharSequence b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.toString().equalsIgnoreCase(b.toString());
    }

    /**
     * 判断 cs 是否包含 searches 中任意一个子串。cs 或 searches 为 null 返回 false。
     */
    public static boolean containsAny(CharSequence cs, CharSequence... searches) {
        if (cs == null || searches == null) {
            return false;
        }
        String s = cs.toString();
        for (CharSequence search : searches) {
            if (search != null && s.contains(search)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 cs 是否以 prefixes 中任意一个前缀开头。cs 或 prefixes 为 null 返回 false。
     */
    public static boolean startsWithAny(CharSequence cs, CharSequence... prefixes) {
        if (cs == null || prefixes == null) {
            return false;
        }
        String s = cs.toString();
        for (CharSequence prefix : prefixes) {
            if (prefix != null && s.startsWith(prefix.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 cs 是否以 suffixes 中任意一个后缀结尾。cs 或 suffixes 为 null 返回 false。
     */
    public static boolean endsWithAny(CharSequence cs, CharSequence... suffixes) {
        if (cs == null || suffixes == null) {
            return false;
        }
        String s = cs.toString();
        for (CharSequence suffix : suffixes) {
            if (suffix != null && s.endsWith(suffix.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * null 安全的空白判断：cs 为 null、长度为 0，或全部字符均为 {@link Character#isWhitespace} 时返回 true。
     * 语义对齐 commons-lang3 StringUtils#isBlank。
     */
    public static boolean isBlank(CharSequence cs) {
        if (cs == null) {
            return true;
        }
        int length = cs.length();
        if (length == 0) {
            return true;
        }
        for (int i = 0; i < length; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@link #isBlank(CharSequence)} 的反义，语义对齐 commons-lang3 StringUtils#isNotBlank。
     */
    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    /**
     * null 安全的去除首尾空白：str 为 null 返回 null，否则返回 {@link String#trim()}。
     * 语义对齐 commons-lang3 StringUtils#trim。
     */
    public static String trim(String str) {
        return str == null ? null : str.trim();
    }
}
