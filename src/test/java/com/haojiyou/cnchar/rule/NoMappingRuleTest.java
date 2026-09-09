package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.core.EditorContext;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.haojiyou.cnchar.settings.MappingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * rule 包单测：{@link NoMappingRule}（纯 JUnit5、脱平台）。
 *
 * <p>验证 O(1) 候选位图门控在规则链中的行为：候选字符放行、非候选字符拒绝，
 * 且候选性随快照（含用户自定义映射）动态变化。
 */
class NoMappingRuleTest {

    private static CharAutoReplaceSettings.Snapshot defaultSnapshot() {
        return CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
    }

    private static EditorContext ctx(char c) {
        return new TestEditorContext(c, "");
    }

    @Test
    @DisplayName("候选字符（全角标点 / 中文标点 / U+3000）放行 → PASS")
    void candidateCharsPass() {
        NoMappingRule rule = new NoMappingRule(defaultSnapshot());
        assertEquals(ReplacementRule.Result.PASS, rule.check(ctx('\uFF0C'))); // ，
        assertEquals(ReplacementRule.Result.PASS, rule.check(ctx('\u3002'))); // 。
        assertEquals(ReplacementRule.Result.PASS, rule.check(ctx('\u3000'))); // 表意空格（候选）
    }

    @Test
    @DisplayName("非候选字符（普通 ASCII / 半角空格）拒绝 → REJECT")
    void nonCandidateCharsRejected() {
        NoMappingRule rule = new NoMappingRule(defaultSnapshot());
        assertEquals(ReplacementRule.Result.REJECT, rule.check(ctx('a')));
        assertEquals(ReplacementRule.Result.REJECT, rule.check(ctx('1')));
        assertEquals(ReplacementRule.Result.REJECT, rule.check(ctx(' ')));
    }

    @Test
    @DisplayName("自定义映射首字符成为候选 → PASS")
    void customMappingFirstCharBecomesCandidate() {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        state.customMappings.add(new MappingRule("a", "b")); // 使 'a' 进入候选位图
        CharAutoReplaceSettings.Snapshot snap = CharAutoReplaceSettings.Snapshot.build(state);
        NoMappingRule rule = new NoMappingRule(snap);
        assertEquals(ReplacementRule.Result.PASS, rule.check(ctx('a')),
                "自定义映射键的首字符应被纳入候选位图");
    }

    /**
     * 测试用 EditorContext 子类，使用 protected 构造绕过 Document/Editor 依赖。
     */
    private static class TestEditorContext extends EditorContext {
        TestEditorContext(char typedChar, String textBefore) {
            super(typedChar, textBefore);
        }
    }
}
