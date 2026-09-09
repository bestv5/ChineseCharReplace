package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.core.EditorContext;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * rule 包单测：{@link BlankInputRule}（纯 JUnit5、脱平台）。
 *
 * <p>守护 Bug：全角空格 {@code U+3000} 是候选字符（应转换为半角空格），但
 * {@code Character.isWhitespace('\u3000') == true}，旧实现用一刀切空白过滤会在规则链首节点
 * 误将其 REJECT，永远到不了 {@code CharConverter}。修复后 BlankInputRule 仅拒绝「非候选空白字符」。
 *
 * <p>{@code u3000Passes} 用例在修复前应失败（旧实现返回 REJECT），修复后通过（PASS）。
 *
 * <p>CJK / 空白字符一律用 Unicode 转义书写，规避源码编码差异。
 */
class BlankInputRuleTest {

    /** 默认快照（不含自定义映射）：候选位图 = 全角区 + U+3000 + 精选 CJK 标点。 */
    private static CharAutoReplaceSettings.Snapshot defaultSnapshot() {
        return CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
    }

    private static BlankInputRule rule() {
        return new BlankInputRule(defaultSnapshot());
    }

    private static EditorContext ctx(char c) {
        return new TestEditorContext(c, "");
    }

    @Test
    @DisplayName("回归守护：U+3000 表意空格（候选空白字符）不被拦截 → PASS")
    void u3000Passes() {
        // 修复前：Character.isWhitespace('\u3000')==true → REJECT（本断言失败）。
        // 修复后：U+3000 是候选字符 → 放行，得以到达 CharConverter 转换为半角空格。
        assertEquals(ReplacementRule.Result.PASS, rule().check(ctx('\u3000')),
                "U+3000 是候选字符，BlankInputRule 必须放行，否则「U+3000→半角空格」失效");
    }

    @Test
    @DisplayName("半角空格 U+0020（非候选空白）被拒绝 → REJECT")
    void asciiSpaceRejected() {
        // 保留 BlankInputRule 对「真正无意义空白」的原始过滤意图。
        assertEquals(ReplacementRule.Result.REJECT, rule().check(ctx(' ')));
    }

    @Test
    @DisplayName("其它非候选空白（制表符 / 换行 / 回车）被拒绝 → REJECT")
    void otherNonCandidateWhitespaceRejected() {
        BlankInputRule r = rule();
        assertEquals(ReplacementRule.Result.REJECT, r.check(ctx('\t')));
        assertEquals(ReplacementRule.Result.REJECT, r.check(ctx('\n')));
        assertEquals(ReplacementRule.Result.REJECT, r.check(ctx('\r')));
    }

    @Test
    @DisplayName("非空白的候选字符正常放行 → PASS")
    void candidateNonWhitespacePasses() {
        BlankInputRule r = rule();
        assertEquals(ReplacementRule.Result.PASS, r.check(ctx('\uFF0C'))); // ，全角逗号
        assertEquals(ReplacementRule.Result.PASS, r.check(ctx('\u3002'))); // 。中文句号
    }

    @Test
    @DisplayName("非空白的非候选字符（普通 ASCII）不被本规则拦截 → PASS（交由 NoMappingRule 处理）")
    void nonCandidateNonWhitespacePassesThisRule() {
        // BlankInputRule 只负责空白过滤；普通 ASCII 的候选性判定属于 NoMappingRule 的职责。
        assertEquals(ReplacementRule.Result.PASS, rule().check(ctx('a')));
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
