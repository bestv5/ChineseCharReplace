package com.haojiyou.cnchar;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * 阶段0 平台集成特征化测试。
 *
 * <p>目标：为后续“注释/字符串区域识别”重构锁定 PSI 基线。CharTypedDocumentLisener.isCanBeReplaced
 * 为 private，难以在 fixture 中直接触发；因此本测试务实地特征化其可测的组成逻辑——
 * 即它所依赖的 PSI(Layer A) 注释判定路径：
 * <pre>
 * PsiElement element = file.findElementAt(caretOffset);
 * PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
 * </pre>
 * （对照 CharTypedDocumentLisener.java:118 / :138 / :147 / :160）
 *
 * <p><b>测试环境事实与覆盖缺口（务必阅读）</b>：
 * <ul>
 *   <li>LightPlatformCodeInsightFixtureTestCase 只引导 <b>平台（含 XML）</b>，不加载 Java/C++/SQL
 *       等语言插件。因此在纯平台 fixture 中，.java/.cpp/.sql 被当作纯文本，PSI(Layer A) 无法把它们的
 *       注释判为 PsiComment——这正是插件保留“扩展名回退层” CnCharCommentUtil.isComment(Layer B)
 *       的根本原因（Layer B 已由纯单测 CnCharCommentUtilIsCommentTest 覆盖）。</li>
 *   <li>要为 Java 建立真实的 PSI 注释基线，需改用 LightJavaCodeInsightFixtureTestCase，而它要求在
 *       build.gradle 的 intellij 块声明 plugins=['java']——超出阶段0“仅新增测试依赖”的许可范围，
 *       故此处不引入，标记为已知缺口，留待后续阶段按需处理。</li>
 *   <li>isCanBeReplaced 的 txt 排除、空白/前驱元素、replaceInComment 三态等分支未在此直接触发；
 *       为测试不改动生产代码可见性。</li>
 * </ul>
 */
public class CommentDetectionIT extends LightPlatformCodeInsightFixtureTestCase {

    /**
     * 复刻 isCanBeReplaced 的 PSI(Layer A) 注释判定：光标处元素是否位于 PsiComment 内。
     * element == null 时按“非注释”处理。
     */
    private boolean isPsiCommentAtCaret() {
        PsiFile file = myFixture.getFile();
        int offset = myFixture.getCaretOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return false;
        }
        return PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null;
    }

    // ---- XML：平台自带 → PSI(Layer A) 能识别注释（真实基线） ----

    public void testXmlCommentDetectedByPsi() {
        myFixture.configureByText("a.xml", "<root><!-- 注<caret>释 --></root>");
        assertTrue("XML 注释内应被 PSI(Layer A) 识别为 PsiComment", isPsiCommentAtCaret());
    }

    public void testXmlCommentBoundaryDetectedByPsi() {
        myFixture.configureByText("c.xml", "<root><!--<caret> x --></root>");
        assertTrue("XML 注释起始边界内应被识别为 PsiComment", isPsiCommentAtCaret());
    }

    public void testXmlContentNotCommentByPsi() {
        myFixture.configureByText("b.xml", "<root>te<caret>xt</root>");
        assertFalse("XML 文本内容不应被识别为 PsiComment", isPsiCommentAtCaret());
    }

    // ---- 纯平台 fixture 未加载语言插件：.java/.cpp/.sql → 纯文本，Layer A 无法分类 ----
    // 记录现状：这解释了为何插件必须有 Layer B（扩展名回退）。此处断言的是“测试环境+架构”事实，
    // 并非“生产环境（装有对应语言插件的 IDE）中 java 注释不是注释”。

    public void testNonBundledLanguagesArePlainTextInPlatformOnlyFixture_layerAGap() {
        myFixture.configureByText("A.java", "class A {\n    int x; // 注<caret>释\n}\n");
        assertFalse("纯平台 fixture 无 Java 插件：.java 为纯文本，PSI 不判为注释（Layer A 缺口）",
                isPsiCommentAtCaret());

        myFixture.configureByText("m.cpp", "// 注<caret>释\nint main() { return 0; }\n");
        assertFalse("纯平台 fixture 无 C++ 插件：.cpp 为纯文本，PSI 不判为注释（Layer A 缺口）",
                isPsiCommentAtCaret());

        myFixture.configureByText("q.sql", "-- 注<caret>释\nSELECT 1;\n");
        assertFalse("纯平台 fixture 无 SQL 插件：.sql 为纯文本，PSI 不判为注释（Layer A 缺口）",
                isPsiCommentAtCaret());
    }
}
