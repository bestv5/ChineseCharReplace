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
}
