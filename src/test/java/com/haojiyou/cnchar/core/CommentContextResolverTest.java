package com.haojiyou.cnchar.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段6 单测：{@link CommentContextResolver#isInsideBlockComment} 纯逻辑
 * （块注释中间行兜底识别，脱平台、语言无关）。
 *
 * <p>覆盖 PSI 不可用语言（C/C++/SQL/XML）多行块注释的中间行判定：
 * 未闭合 / 已闭合 / 相邻成对 / 悬空结束符 / 空标记守卫。
 */
class CommentContextResolverTest {

    private static final String P = "/*";
    private static final String S = "*/";

    @Test
    @DisplayName("未闭合块注释的中间行 → 判为注释内")
    void unclosedBlockIsInside() {
        assertTrue(CommentContextResolver.isInsideBlockComment("int x; " + P + " hello ", P, S));
    }

    @Test
    @DisplayName("已闭合块注释之后 → 非注释内")
    void closedBlockIsOutside() {
        assertFalse(CommentContextResolver.isInsideBlockComment(P + " hello " + S + " int y;", P, S));
    }

    @Test
    @DisplayName("多行块注释第二行（caret 在行中，行首非 /*）→ 仍判为注释内")
    void multilineInteriorLineIsInside() {
        assertTrue(CommentContextResolver.isInsideBlockComment(P + " line1\n line2 ", P, S));
    }

    @Test
    @DisplayName("相邻两块，光标在第二块内 → 注释内")
    void secondAdjacentBlockIsInside() {
        assertTrue(CommentContextResolver.isInsideBlockComment(P + " a " + S + " code " + P + " b ", P, S));
    }

    @Test
    @DisplayName("相邻两块均闭合，光标在末尾 → 非注释内")
    void bothAdjacentBlocksClosedIsOutside() {
        assertFalse(CommentContextResolver.isInsideBlockComment(P + " a " + S + " code " + P + " b " + S + " ", P, S));
    }

    @Test
    @DisplayName("悬空结束符（先出现 */ 而无 /*）→ 非注释内")
    void danglingSuffixIsOutside() {
        assertFalse(CommentContextResolver.isInsideBlockComment(S + " x ", P, S));
    }

    @Test
    @DisplayName("空光标前文本 → 非注释内")
    void emptyBeforeIsOutside() {
        assertFalse(CommentContextResolver.isInsideBlockComment("", P, S));
    }

    @Test
    @DisplayName("null / 空标记守卫 → 非注释内")
    void nullOrBlankMarkersGuard() {
        assertFalse(CommentContextResolver.isInsideBlockComment(null, P, S));
        assertFalse(CommentContextResolver.isInsideBlockComment("anything", null, S));
        assertFalse(CommentContextResolver.isInsideBlockComment("anything", P, null));
        assertFalse(CommentContextResolver.isInsideBlockComment("anything", "", S));
        assertFalse(CommentContextResolver.isInsideBlockComment("anything", P, ""));
    }
}
