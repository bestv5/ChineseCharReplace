package com.haojiyou.cnchar.settings;

import com.haojiyou.cnchar.region.InputRegion;

/**
 * 描述: 每个区域统一的三态替换策略容器（纯逻辑，无 IDE 依赖，可脱平台单测）。
 *
 * <p>所有区域共用同一套三态 {@link ReplaceMode}（开 ALWAYS / 关 NEVER / 自适应 ADAPTIVE）。
 * “每区一个策略”的可序列化承载放在 {@link CharAutoReplaceSettings.State} 的 public 枚举字段上
 * （便于 {@code XmlSerializer} 按字段名序列化）；本类只提供枚举与各区域的默认值。
 *
 * @author : best.xu
 */
public final class RegionPolicy {

    private RegionPolicy() {
    }

    /**
     * 区域替换三态。
     */
    public enum ReplaceMode {
        /** 开：该区域内候选字符一律替换。 */
        ALWAYS,
        /** 关：该区域内一律不替换。 */
        NEVER,
        /** 自适应：按光标前文中英文语境启发式决定（阶段3 的 CjkContextDetector 实现）。 */
        ADAPTIVE
    }

    /**
     * 各区域的默认策略（spec 区域策略表 / 假设 1）。
     * CODE=ALWAYS、COMMENT=ADAPTIVE、STRING=ADAPTIVE、COMMIT=ADAPTIVE、
     * CONSOLE=NEVER、PLAIN_TEXT=ADAPTIVE、UNREACHABLE=NEVER（恒排除）。
     */
    public static ReplaceMode defaultMode(InputRegion region) {
        if (region == null) {
            return ReplaceMode.NEVER;
        }
        switch (region) {
            case CODE:
                return ReplaceMode.ALWAYS;
            case COMMENT:
            case STRING:
            case COMMIT:
            case PLAIN_TEXT:
                return ReplaceMode.ADAPTIVE;
            case CONSOLE:
            case UNREACHABLE:
            default:
                return ReplaceMode.NEVER;
        }
    }
}
