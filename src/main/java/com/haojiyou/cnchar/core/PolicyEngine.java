package com.haojiyou.cnchar.core;

import com.haojiyou.cnchar.region.InputRegion;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.haojiyou.cnchar.settings.RegionPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 区域统一三态策略决策引擎（纯逻辑，无 IDE 依赖，可脱平台单测）。
 *
 * <p>输入：{@link InputRegion} + 当前快照的 {@link RegionPolicy.ReplaceMode}。
 * 输出：{@link Decision#REPLACE} 或 {@link Decision#SKIP}。
 *
 * <p>决策矩阵：
 * <ul>
 *   <li>ALWAYS → REPLACE</li>
 *   <li>NEVER → SKIP</li>
 *   <li>ADAPTIVE → 调用 {@link CjkContextDetector}：中文语境 → SKIP；英文语境 → REPLACE</li>
 * </ul>
 *
 * @author : best.xu
 */
public final class PolicyEngine {

    private PolicyEngine() {
    }

    /**
     * 策略决策结果。
     */
    public enum Decision {
        REPLACE,
        SKIP
    }

    /**
     * 根据区域与模式做出替换决策。
     *
     * @param region   当前输入区域
     * @param ctx      击键上下文（ADAPTIVE 时用于 CJK 语境检测）
     * @param snapshot 当前配置快照
     * @return REPLACE 或 SKIP
     */
    @NotNull
    public static Decision decide(@NotNull InputRegion region,
                                  @NotNull EditorContext ctx,
                                  @NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        RegionPolicy.ReplaceMode mode = snapshot.getMode(region);
        switch (mode) {
            case ALWAYS:
                return Decision.REPLACE;
            case NEVER:
                return Decision.SKIP;
            case ADAPTIVE:
                return CjkContextDetector.isChineseContext(ctx.getTextBeforeCursor())
                        ? Decision.SKIP
                        : Decision.REPLACE;
            default:
                return Decision.SKIP;
        }
    }
}
