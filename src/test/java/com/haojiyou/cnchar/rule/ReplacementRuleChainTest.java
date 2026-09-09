package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.core.EditorContext;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * rule 包单测：{@link ReplacementRuleChain} 链路整体行为（纯 JUnit5、脱平台）。
 *
 * <p>验证前置过滤链在真实节点顺序（BlankInputRule → NoMappingRule → InputLengthRule）下的整体判定，
 * 重点守护「U+3000 能穿过整条链路」这一 Bug 修复：任一节点误拒都会导致后续 CharConverter 无法执行。
 */
class ReplacementRuleChainTest {

    private static CharAutoReplaceSettings.Snapshot defaultSnapshot() {
        return CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
    }

    private static ReplacementRuleChain defaultChain() {
        return ReplacementRuleChain.defaultChain(defaultSnapshot());
    }

    private static EditorContext ctx(char c) {
        return new TestEditorContext(c, "");
    }

    @Test
    @DisplayName("回归守护：U+3000 能穿过默认规则链（checkAll == true）")
    void u3000PassesChain() {
        // 修复前：BlankInputRule 在链首 REJECT，checkAll 返回 false，U+3000 永远到不了转换引擎。
        assertTrue(defaultChain().checkAll(ctx('\u3000')),
                "U+3000 应穿过整条链路，进入区域/策略/转换阶段");
    }

    @Test
    @DisplayName("候选字符（全角标点 / 中文标点）能穿过默认规则链")
    void candidateCharsPassChain() {
        ReplacementRuleChain chain = defaultChain();
        assertTrue(chain.checkAll(ctx('\uFF0C'))); // ，全角逗号
        assertTrue(chain.checkAll(ctx('\u3002'))); // 。中文句号
        assertTrue(chain.checkAll(ctx('\u2026'))); // …一对多
    }

    @Test
    @DisplayName("非候选空白（半角空格 / 制表符）被链路拒绝（checkAll == false）")
    void nonCandidateWhitespaceRejectedByChain() {
        ReplacementRuleChain chain = defaultChain();
        assertFalse(chain.checkAll(ctx(' ')),  "半角空格：BlankInputRule 拒");
        assertFalse(chain.checkAll(ctx('\t')), "制表符：BlankInputRule 拒");
    }

    @Test
    @DisplayName("非候选非空白（普通 ASCII）被链路拒绝（NoMappingRule 生效）")
    void nonCandidateAsciiRejectedByChain() {
        ReplacementRuleChain chain = defaultChain();
        assertFalse(chain.checkAll(ctx('a')), "普通 ASCII：BlankInputRule 放行后由 NoMappingRule 拒");
        assertFalse(chain.checkAll(ctx('1')));
    }

    @Test
    @DisplayName("空规则链恒通过（checkAll == true）")
    void emptyChainPasses() {
        ReplacementRuleChain chain = new ReplacementRuleChain(Collections.emptyList());
        assertTrue(chain.checkAll(ctx('x')));
    }

    @Test
    @DisplayName("单一 REJECT 规则使链路整体拒绝")
    void singleRejectRuleFailsChain() {
        ReplacementRule alwaysReject = c -> ReplacementRule.Result.REJECT;
        ReplacementRuleChain chain = new ReplacementRuleChain(Arrays.asList(alwaysReject));
        assertFalse(chain.checkAll(ctx('\u3000')));
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
