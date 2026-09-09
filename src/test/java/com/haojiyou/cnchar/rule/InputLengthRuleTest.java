package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.core.EditorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * rule 包单测：{@link InputLengthRule}（纯 JUnit5、脱平台）。
 *
 * <p>当前实现：typedHandler 每次仅传入单字符，故此规则恒 PASS（预留批量粘贴等未来扩展接口）。
 * 本测试锁定该契约，防止后续误改导致单字符场景被拒绝。
 */
class InputLengthRuleTest {

    private static EditorContext ctx(char c) {
        return new TestEditorContext(c, "");
    }

    @Test
    @DisplayName("单字符输入恒 PASS（含候选与非候选字符）")
    void singleCharAlwaysPasses() {
        InputLengthRule rule = new InputLengthRule();
        assertEquals(ReplacementRule.Result.PASS, rule.check(ctx('\u3000'))); // 表意空格
        assertEquals(ReplacementRule.Result.PASS, rule.check(ctx('\uFF0C'))); // 全角逗号
        assertEquals(ReplacementRule.Result.PASS, rule.check(ctx('a')));      // 普通 ASCII
        assertEquals(ReplacementRule.Result.PASS, rule.check(ctx(' ')));      // 半角空格
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
