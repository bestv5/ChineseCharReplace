package com.haojiyou.cnchar;

import com.haojiyou.cnchar.convert.CharConverter;
import com.haojiyou.cnchar.core.EditorContext;
import com.haojiyou.cnchar.core.PolicyEngine;
import com.haojiyou.cnchar.core.RegionClassifier;
import com.haojiyou.cnchar.core.ReplacementExecutor;
import com.haojiyou.cnchar.region.InputRegion;
import com.haojiyou.cnchar.rule.ReplacementRuleChain;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.haojiyou.cnchar.settings.RegionPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * 描述: 沙箱等价验证 —— 三态区域策略端到端（真实 IC-2020.3 平台 + 真实 Editor/PSI + 真实决策链）。
 *
 * <p><b>为何用本类替代手工 runIde 点击</b>：{@link com.haojiyou.cnchar.handler.ChineseCharCheckHandler#charTyped}
 * 的 snapshot 取自应用级服务、无法按用例注入，故本类以「逐段复刻生产决策链」的方式驱动
 * O(1) 候选门控 → commitDocument → 前置规则链 → 区域分类 → 策略决策 → 推迟执行，
 * 除 AWT 键入分发（IME 组合输入）外，与沙箱运行期走完全相同的判定与写入路径。
 *
 * <p><b>锁定语义（设计规格）</b>：COMMENT=ADAPTIVE 时中文语境 SKIP、英文语境 REPLACE；
 * COMMENT=ALWAYS 时中文注释内候选字符一律替换（含表意空格 U+3000 → 半角空格，
 * 守护历史上被 {@code BlankInputRule} 误拦截的 P0 缺陷复发）；STRING 区中文字面量保持不变。
 *
 * <p>运行方式同 {@code ReplacementExecutorIT}：随 {@code gradlew test} 以 headless 平台 fixture 执行，
 * 也可 {@code gradlew test --tests "com.haojiyou.cnchar.SandboxRegionPolicyIT"} 单独运行。
 *
 * @author : best.xu
 */
public class SandboxRegionPolicyIT extends LightPlatformCodeInsightFixtureTestCase {

    /** 一次管线运行的可观测结果（用于把「区域判定」与「是否真的改写文档」同时断言出来）。 */
    private static final class Outcome {
        InputRegion region;
        boolean chainPassed = true;
        boolean replaced;
        String text;
    }

    /** 指定 COMMENT / STRING 区域策略的快照（其余取默认）。 */
    private static CharAutoReplaceSettings.Snapshot snapshotOf(RegionPolicy.ReplaceMode commentMode,
                                                               RegionPolicy.ReplaceMode stringMode) {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        state.commentMode = commentMode;
        state.stringMode = stringMode;
        state.showHint = false;
        return CharAutoReplaceSettings.Snapshot.build(state);
    }

    /**
     * 逐段复刻 {@code ChineseCharCheckHandler.charTyped} 决策链（defer 经泵 EDT 完成写入）。
     *
     * @param typedChar 刚键入字符（文档中已位于 caret-1）
     */
    private Outcome runPipeline(char typedChar, CharAutoReplaceSettings.Snapshot snapshot) {
        Outcome outcome = new Outcome();
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        String before = document.getText();

        outcome.replaced = false;
        outcome.text = before;
        if (!snapshot.isCandidate(typedChar)) {
            return outcome;
        }

        int offset = myFixture.getCaretOffset();
        PsiDocumentManager.getInstance(getProject()).commitDocument(document);

        EditorContext ctx = new EditorContext(
                getProject(), editor, document, myFixture.getFile(), offset, typedChar);

        outcome.chainPassed = ReplacementRuleChain.defaultChain(snapshot).checkAll(ctx);
        if (!outcome.chainPassed) {
            return outcome;
        }

        outcome.region = RegionClassifier.classify(ctx);
        if (outcome.region == InputRegion.UNREACHABLE) {
            return outcome;
        }
        if (PolicyEngine.decide(outcome.region, ctx, snapshot) == PolicyEngine.Decision.SKIP) {
            return outcome;
        }

        ReplacementExecutor.execute(ctx, new CharConverter(snapshot.getCharMap()), snapshot);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        outcome.text = document.getText();
        outcome.replaced = !before.equals(outcome.text);
        return outcome;
    }

    // ---- COMMENT 区 ----

    /** COMMENT=ADAPTIVE（默认）+ 英文语境注释：全角逗号应替换为半角逗号。 */
    public void testCommentAdaptiveEnglishContextReplacesComma() {
        myFixture.configureByText("a.xml", "<root><!-- hello \uFF0C<caret> --></root>");
        Outcome o = runPipeline('\uFF0C', snapshotOf(RegionPolicy.ReplaceMode.ADAPTIVE,
                RegionPolicy.ReplaceMode.ADAPTIVE));

        assertEquals("XML 注释内应分类为 COMMENT 区", InputRegion.COMMENT, o.region);
        assertTrue("英文语境（前文 hello）应放行至执行器完成替换", o.replaced);
        assertEquals("<root><!-- hello , --></root>", o.text);
    }

    /** COMMENT=ADAPTIVE（默认）+ 中文语境注释：设计规定视为中文语境，SKIP 不改写（特征化锁定）。 */
    public void testCommentAdaptiveChineseContextKeepsComma() {
        myFixture.configureByText("a.xml", "<root><!-- 中文说明\uFF0C<caret> --></root>");
        Outcome o = runPipeline('\uFF0C', snapshotOf(RegionPolicy.ReplaceMode.ADAPTIVE,
                RegionPolicy.ReplaceMode.ADAPTIVE));

        assertEquals("仍应分类为 COMMENT 区", InputRegion.COMMENT, o.region);
        assertFalse("ADAPTIVE + 中文语境不替换（若需中文注释内强制转换，须把注释区设为「开」）", o.replaced);
        assertEquals("<root><!-- 中文说明\uFF0C --></root>", o.text);
    }

    /** COMMENT=ALWAYS（注释区设为「开」）+ 中文语境：全角逗号必须转换。 */
    public void testCommentAlwaysReplacesCommaInChineseContext() {
        myFixture.configureByText("a.xml", "<root><!-- 中文说明\uFF0C<caret> --></root>");
        Outcome o = runPipeline('\uFF0C', snapshotOf(RegionPolicy.ReplaceMode.ALWAYS,
                RegionPolicy.ReplaceMode.ADAPTIVE));

        assertEquals(InputRegion.COMMENT, o.region);
        assertTrue("ALWAYS 无语境启发式，中文注释内全角逗号应转半角", o.replaced);
        assertEquals("<root><!-- 中文说明, --></root>", o.text);
    }

    /**
     * COMMENT=ALWAYS + 中文注释内表意空格 U+3000：应穿过规则链并替换为半角空格。
     *
     * <p>端到端守护 P0 缺陷复发：{@code BlankInputRule} 曾以 {@code Character.isWhitespace}
     * 把候选字符 U+3000 拦在链首，使其永不到达转换引擎。
     */
    public void testCommentAlwaysReplacesIdeographicSpace() {
        myFixture.configureByText("a.xml", "<root><!-- 中文说明\u3000<caret> --></root>");
        Outcome o = runPipeline('\u3000', snapshotOf(RegionPolicy.ReplaceMode.ALWAYS,
                RegionPolicy.ReplaceMode.ADAPTIVE));

        assertTrue("U+3000 是候选字符，不得被 BlankInputRule 拦截（chainPassed 应为 true）",
                o.chainPassed);
        assertEquals(InputRegion.COMMENT, o.region);
        assertTrue("表意空格应替换为半角空格", o.replaced);
        assertEquals("<root><!-- 中文说明  --></root>", o.text);
    }

    /** COMMENT=ADAPTIVE + 英文语境注释内的表意空格：同样应替换（链路口径与中文语境一致）。 */
    public void testCommentAdaptiveEnglishContextReplacesIdeographicSpace() {
        myFixture.configureByText("a.xml", "<root><!-- hello\u3000<caret> --></root>");
        Outcome o = runPipeline('\u3000', snapshotOf(RegionPolicy.ReplaceMode.ADAPTIVE,
                RegionPolicy.ReplaceMode.ADAPTIVE));

        assertTrue("规则链不得拦截 U+3000", o.chainPassed);
        assertTrue("英文语境注释内表意空格应替换", o.replaced);
        assertEquals("<root><!-- hello  --></root>", o.text);
    }

    // ---- STRING 区 ----

    /** STRING=ADAPTIVE（默认）+ 中文字面量：全角逗号保持不变（用户可见预期「字符串不动」）。 */
    public void testStringLiteralKeepsFullWidthComma() {
        myFixture.configureByText("a.xml", "<root attr=\"中文值\uFF0C<caret>\"/>");
        Outcome o = runPipeline('\uFF0C', snapshotOf(RegionPolicy.ReplaceMode.ALWAYS,
                RegionPolicy.ReplaceMode.ADAPTIVE));

        assertEquals("属性值应分类为 STRING 区", InputRegion.STRING, o.region);
        assertFalse("中文字面量内的全角逗号不应被改写", o.replaced);
        assertEquals("<root attr=\"中文值\uFF0C\"/>", o.text);
    }

    /** STRING=NEVER（字符串区设为「关」）：任意语境均不改写。 */
    public void testStringNeverModeKeepsContent() {
        myFixture.configureByText("a.xml", "<root attr=\"hello\uFF0C<caret>\"/>");
        Outcome o = runPipeline('\uFF0C', snapshotOf(RegionPolicy.ReplaceMode.ALWAYS,
                RegionPolicy.ReplaceMode.NEVER));

        assertEquals(InputRegion.STRING, o.region);
        assertFalse("STRING=NEVER 一律不替换", o.replaced);
        assertEquals("<root attr=\"hello\uFF0C\"/>", o.text);
    }
}
