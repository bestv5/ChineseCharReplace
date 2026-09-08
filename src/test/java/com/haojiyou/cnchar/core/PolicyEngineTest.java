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

    /**
     * 测试用 EditorContext 子类，使用 protected 构造绕过 Document 依赖。
     */
    private static class TestEditorContext extends EditorContext {
        TestEditorContext(char typedChar, String textBefore) {
            super(typedChar, textBefore);
        }
    }
}
