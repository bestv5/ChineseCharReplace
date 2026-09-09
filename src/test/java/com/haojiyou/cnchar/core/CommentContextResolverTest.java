package com.haojiyou.cnchar.core;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * SyntaxHighlighter token 兜底层纯逻辑：key 数组 → 上下文类型映射。
     *
     * <p>token 兜底层用于 PSI 主判未命中的语言（如未实现注入接口的 C++/SQL/CSS），
     * 本组用例守护其唯一可脱平台验证的核心：{@link CommentContextResolver#mapKeysToContext}。
     * live 高亮器 / EditorEx / offset 回退定位须平台 fixture，记录为 runIde 实测项。
     */
    @Nested
    @DisplayName("token 兜底层：key 数组 → 上下文映射（mapKeysToContext）")
    class TokenKeyMapping {

        @Test
        @DisplayName("STRING key → STRING")
        void stringKeyMapsToString() {
            assertEquals(CommentContextResolver.ContextType.STRING,
                    CommentContextResolver.mapKeysToContext(
                            new TextAttributesKey[]{DefaultLanguageHighlighterColors.STRING}));
        }

        @Test
        @DisplayName("行注释 / 块注释 / 文档注释 key → COMMENT（无单一 COMMENT 常量，三者分别命中）")
        void commentKeysMapToComment() {
            for (TextAttributesKey commentKey : new TextAttributesKey[]{
                    DefaultLanguageHighlighterColors.LINE_COMMENT,
                    DefaultLanguageHighlighterColors.BLOCK_COMMENT,
                    DefaultLanguageHighlighterColors.DOC_COMMENT}) {
                assertEquals(CommentContextResolver.ContextType.COMMENT,
                        CommentContextResolver.mapKeysToContext(new TextAttributesKey[]{commentKey}),
                        commentKey.getExternalName());
            }
        }

        @Test
        @DisplayName("STRING 与 COMMENT key 复合叠加（如 docstring）→ 取更保守的 COMMENT")
        void commentWinsOverStringWhenBothPresent() {
            assertEquals(CommentContextResolver.ContextType.COMMENT,
                    CommentContextResolver.mapKeysToContext(new TextAttributesKey[]{
                            DefaultLanguageHighlighterColors.STRING,
                            DefaultLanguageHighlighterColors.BLOCK_COMMENT}));
        }

        @Test
        @DisplayName("语言自定义 key（非平台默认语义 key）→ 不命中（已知局限，见 mapKeysToContext javadoc）")
        void languageCustomKeyDoesNotMatch() {
            TextAttributesKey customStringKey =
                    TextAttributesKey.createTextAttributesKey("SOME_LANG_CUSTOM_STRING");
            assertNull(CommentContextResolver.mapKeysToContext(new TextAttributesKey[]{customStringKey}));
        }

        @Test
        @DisplayName("混合数组：null 元素 / 无关 key 混入 + STRING → STRING（容忍 null 元素）")
        void mixedArrayWithNullAndIrrelevantKeys() {
            assertEquals(CommentContextResolver.ContextType.STRING,
                    CommentContextResolver.mapKeysToContext(new TextAttributesKey[]{
                            null,
                            TextAttributesKey.createTextAttributesKey("TEST_OTHER_TOKEN"),
                            DefaultLanguageHighlighterColors.STRING}));
        }

        @Test
        @DisplayName("全部为无关 key → null（非注释非字符串 token，交由 Commenter/默认路径）")
        void allIrrelevantKeysMapToNull() {
            assertNull(CommentContextResolver.mapKeysToContext(new TextAttributesKey[]{
                    TextAttributesKey.createTextAttributesKey("TEST_PLAIN_TOKEN"),
                    TextAttributesKey.createTextAttributesKey("TEST_ANOTHER_TOKEN")}));
        }

        @Test
        @DisplayName("null / 空数组 → null（守卫）")
        void nullOrEmptyGuard() {
            assertNull(CommentContextResolver.mapKeysToContext(null));
            assertNull(CommentContextResolver.mapKeysToContext(new TextAttributesKey[0]));
        }
    }
}
