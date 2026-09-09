package com.haojiyou.cnchar.core;

import com.haojiyou.cnchar.region.InputRegion;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.haojiyou.cnchar.settings.RegionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 阶段3 单测：策略决策引擎（纯逻辑，脱平台）。
 */
class PolicyEngineTest {

    private static EditorContext mockCtx(String textBefore) {
        return new TestEditorContext('x', textBefore);
    }

    @Test
    @DisplayName("ALWAYS → REPLACE")
    void alwaysModeReplaces() {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        state.codeMode = RegionPolicy.ReplaceMode.ALWAYS;
        CharAutoReplaceSettings.Snapshot snap = CharAutoReplaceSettings.Snapshot.build(state);
        assertEquals(PolicyEngine.Decision.REPLACE,
                PolicyEngine.decide(InputRegion.CODE, mockCtx("abc"), snap));
    }

    @Test
    @DisplayName("NEVER → SKIP")
    void neverModeSkips() {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        state.consoleMode = RegionPolicy.ReplaceMode.NEVER;
        CharAutoReplaceSettings.Snapshot snap = CharAutoReplaceSettings.Snapshot.build(state);
        assertEquals(PolicyEngine.Decision.SKIP,
                PolicyEngine.decide(InputRegion.CONSOLE, mockCtx("abc"), snap));
    }

    @Test
    @DisplayName("ADAPTIVE + 英文前文 → REPLACE")
    void adaptiveWithEnglishPrefixReplaces() {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        state.commentMode = RegionPolicy.ReplaceMode.ADAPTIVE;
        CharAutoReplaceSettings.Snapshot snap = CharAutoReplaceSettings.Snapshot.build(state);
        assertEquals(PolicyEngine.Decision.REPLACE,
                PolicyEngine.decide(InputRegion.COMMENT, mockCtx("hello"), snap));
    }

    @Test
    @DisplayName("ADAPTIVE + 中文前文 → SKIP")
    void adaptiveWithChinesePrefixSkips() {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        state.commentMode = RegionPolicy.ReplaceMode.ADAPTIVE;
        CharAutoReplaceSettings.Snapshot snap = CharAutoReplaceSettings.Snapshot.build(state);
        assertEquals(PolicyEngine.Decision.SKIP,
                PolicyEngine.decide(InputRegion.COMMENT, mockCtx("\u4f60\u597d"), snap));
    }

    @Test
    @DisplayName("ADAPTIVE + 空前文 → SKIP（保守）")
    void adaptiveWithEmptyPrefixSkips() {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        state.commentMode = RegionPolicy.ReplaceMode.ADAPTIVE;
        CharAutoReplaceSettings.Snapshot snap = CharAutoReplaceSettings.Snapshot.build(state);
        assertEquals(PolicyEngine.Decision.SKIP,
                PolicyEngine.decide(InputRegion.COMMENT, mockCtx(""), snap));
    }

    @Test
    @DisplayName("UNREACHABLE → SKIP（恒排除）")
    void unreachableAlwaysSkips() {
        CharAutoReplaceSettings.Snapshot snap =
                CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
        assertEquals(PolicyEngine.Decision.SKIP,
                PolicyEngine.decide(InputRegion.UNREACHABLE, mockCtx("abc"), snap));
    }

    // ==================================================================================
    // 全区 × 三态矩阵（测试计划 R50：PolicyEngine 覆盖「各区域 ALWAYS/NEVER/ADAPTIVE 矩阵」）
    //   · 6 个可替换区域：CODE / COMMENT / STRING / COMMIT / CONSOLE / PLAIN_TEXT
    //   · 3 态：ALWAYS→REPLACE、NEVER→SKIP、ADAPTIVE→按 CJK 语境（中文 SKIP / 英文 REPLACE / 空 SKIP）
    //   · UNREACHABLE 无独立策略字段，恒 NEVER（单独断言其排除语义）
    // 说明：本模块为纯逻辑脱平台单测，不使用 junit-jupiter-params（未在 build.gradle 声明该依赖），
    //       改以「循环 + 带区域名的断言消息」实现等价的全矩阵覆盖。
    // ==================================================================================

    /** 6 个可替换区域（UNREACHABLE 恒排除，另行断言）。 */
    private static final InputRegion[] REPLACEABLE_REGIONS = {
            InputRegion.CODE, InputRegion.COMMENT, InputRegion.STRING,
            InputRegion.COMMIT, InputRegion.CONSOLE, InputRegion.PLAIN_TEXT
    };

    /** 按区域写入 State 对应字段（UNREACHABLE 无字段，恒 NEVER，忽略）。 */
    private static void setMode(CharAutoReplaceSettings.State state,
                                InputRegion region,
                                RegionPolicy.ReplaceMode mode) {
        switch (region) {
            case CODE:
                state.codeMode = mode;
                break;
            case COMMENT:
                state.commentMode = mode;
                break;
            case STRING:
                state.stringMode = mode;
                break;
            case COMMIT:
                state.commitMode = mode;
                break;
            case CONSOLE:
                state.consoleMode = mode;
                break;
            case PLAIN_TEXT:
                state.plainTextMode = mode;
                break;
            default:
                break; // UNREACHABLE 无独立字段，恒 NEVER
        }
    }

    /** 构造仅将指定区域设为指定模式的快照（其余区域取默认值）。 */
    private static CharAutoReplaceSettings.Snapshot snapshotWith(InputRegion region,
                                                                 RegionPolicy.ReplaceMode mode) {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        setMode(state, region, mode);
        return CharAutoReplaceSettings.Snapshot.build(state);
    }

    @Test
    @DisplayName("矩阵 · 6 区 × ALWAYS → REPLACE（即使中文语境也强制替换）")
    void matrixAlwaysReplacesAllRegions() {
        for (InputRegion region : REPLACEABLE_REGIONS) {
            CharAutoReplaceSettings.Snapshot snap = snapshotWith(region, RegionPolicy.ReplaceMode.ALWAYS);
            assertEquals(PolicyEngine.Decision.REPLACE,
                    PolicyEngine.decide(region, mockCtx("\u4f60\u597d"), snap),
                    region + " × ALWAYS 应 REPLACE");
        }
    }

    @Test
    @DisplayName("矩阵 · 6 区 × NEVER → SKIP（即使英文语境也强制不替换）")
    void matrixNeverSkipsAllRegions() {
        for (InputRegion region : REPLACEABLE_REGIONS) {
            CharAutoReplaceSettings.Snapshot snap = snapshotWith(region, RegionPolicy.ReplaceMode.NEVER);
            assertEquals(PolicyEngine.Decision.SKIP,
                    PolicyEngine.decide(region, mockCtx("hello"), snap),
                    region + " × NEVER 应 SKIP");
        }
    }

    @Test
    @DisplayName("矩阵 · 6 区 × ADAPTIVE + 中文语境 → SKIP")
    void matrixAdaptiveChineseContextSkipsAllRegions() {
        for (InputRegion region : REPLACEABLE_REGIONS) {
            CharAutoReplaceSettings.Snapshot snap = snapshotWith(region, RegionPolicy.ReplaceMode.ADAPTIVE);
            assertEquals(PolicyEngine.Decision.SKIP,
                    PolicyEngine.decide(region, mockCtx("\u8fd9\u662f\u4e2d\u6587"), snap),
                    region + " × ADAPTIVE + 中文语境 应 SKIP");
        }
    }

    @Test
    @DisplayName("矩阵 · 6 区 × ADAPTIVE + 英文语境 → REPLACE")
    void matrixAdaptiveEnglishContextReplacesAllRegions() {
        for (InputRegion region : REPLACEABLE_REGIONS) {
            CharAutoReplaceSettings.Snapshot snap = snapshotWith(region, RegionPolicy.ReplaceMode.ADAPTIVE);
            assertEquals(PolicyEngine.Decision.REPLACE,
                    PolicyEngine.decide(region, mockCtx("hello world"), snap),
                    region + " × ADAPTIVE + 英文语境 应 REPLACE");
        }
    }

    @Test
    @DisplayName("矩阵 · 6 区 × ADAPTIVE + 空前文 → SKIP（保守）")
    void matrixAdaptiveEmptyPrefixSkipsAllRegions() {
        for (InputRegion region : REPLACEABLE_REGIONS) {
            CharAutoReplaceSettings.Snapshot snap = snapshotWith(region, RegionPolicy.ReplaceMode.ADAPTIVE);
            assertEquals(PolicyEngine.Decision.SKIP,
                    PolicyEngine.decide(region, mockCtx(""), snap),
                    region + " × ADAPTIVE + 空前文 应保守 SKIP");
        }
    }

    @Test
    @DisplayName("矩阵 · UNREACHABLE 恒 SKIP（任意语境均排除，无独立策略字段）")
    void matrixUnreachableAlwaysSkips() {
        CharAutoReplaceSettings.Snapshot def =
                CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
        assertEquals(RegionPolicy.ReplaceMode.NEVER, def.getMode(InputRegion.UNREACHABLE),
                "UNREACHABLE 快照模式应恒为 NEVER");
        assertEquals(PolicyEngine.Decision.SKIP,
                PolicyEngine.decide(InputRegion.UNREACHABLE, mockCtx("hello"), def),
                "UNREACHABLE 英文语境也应 SKIP");
        assertEquals(PolicyEngine.Decision.SKIP,
                PolicyEngine.decide(InputRegion.UNREACHABLE, mockCtx("\u4f60\u597d"), def),
                "UNREACHABLE 中文语境也应 SKIP");
    }

    @Test
    @DisplayName("矩阵 · 默认策略基线：CODE=ALWAYS、CONSOLE=NEVER、其余=ADAPTIVE")
    void matrixDefaultPolicyBaseline() {
        CharAutoReplaceSettings.Snapshot def =
                CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
        // CODE 默认 ALWAYS → 中文语境仍 REPLACE
        assertEquals(PolicyEngine.Decision.REPLACE,
                PolicyEngine.decide(InputRegion.CODE, mockCtx("\u4f60\u597d"), def));
        // CONSOLE 默认 NEVER → 英文语境仍 SKIP
        assertEquals(PolicyEngine.Decision.SKIP,
                PolicyEngine.decide(InputRegion.CONSOLE, mockCtx("hello"), def));
        // COMMENT/STRING/COMMIT/PLAIN_TEXT 默认 ADAPTIVE → 英文 REPLACE、中文 SKIP
        InputRegion[] adaptiveDefaults = {
                InputRegion.COMMENT, InputRegion.STRING, InputRegion.COMMIT, InputRegion.PLAIN_TEXT
        };
        for (InputRegion region : adaptiveDefaults) {
            assertEquals(PolicyEngine.Decision.REPLACE,
                    PolicyEngine.decide(region, mockCtx("code"), def),
                    region + " 默认 ADAPTIVE + 英文 应 REPLACE");
            assertEquals(PolicyEngine.Decision.SKIP,
                    PolicyEngine.decide(region, mockCtx("\u6ce8\u91ca"), def),
                    region + " 默认 ADAPTIVE + 中文 应 SKIP");
        }
    }

    /**
     * 测试用 EditorContext 子类，使用 protected 构造绕过 Document 依赖。
     */
    private static class TestEditorContext extends EditorContext {
        TestEditorContext(char typedChar, String textBefore) {
            super(typedChar, textBefore);
        }
    }
}
