package com.haojiyou.cnchar.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段0 特征化测试：锁定 {@link CnCharCommentUtil#isComment(String, SupportFileType)} 各分支。
 *
 * <p>算法（对照源码 :90-106）：
 * <ol>
 *   <li>fileType == null → 直接返回 true（“不支持的类型按注释处理，不替换”）。</li>
 *   <li>取 COMMENT_START_MAP / COMMENT_END_MAP 中该类型的标记。</li>
 *   <li>仅当 commentStartFlags != null 时才判定：先 endsWithAny(trim(line), endFlags)，
 *       未命中再 startsWithAny(trim(line), startFlags)。</li>
 *   <li>commentStartFlags == null（未登记注释标记的类型）→ 恒返回 false。</li>
 * </ol>
 *
 * <p>本测试如实记录现状，<b>包括已知 bug</b>（HTML/CSS/PROPERTIES 未登记注释标记 → 恒 false），
 * 特征化 = 记录，不在本阶段修复。
 */
class CnCharCommentUtilIsCommentTest {

    @Nested
    @DisplayName("fileType == null 分支")
    class NullFileType {
        @Test
        @DisplayName("null 类型 → 恒返回 true（视为注释，不替换）")
        void nullType_returnsTrue() {
            assertTrue(CnCharCommentUtil.isComment("anything", null));
            assertTrue(CnCharCommentUtil.isComment("", null));
            assertTrue(CnCharCommentUtil.isComment("int x = 1;", null));
        }
    }

    @Nested
    @DisplayName("JAVA 分支: // 与 /* */")
    class Java {
        @Test
        void lineCommentStart_true() {
            assertTrue(CnCharCommentUtil.isComment("// foo", SupportFileType.JAVA));
        }

        @Test
        void blockCommentStart_true() {
            assertTrue(CnCharCommentUtil.isComment("/* foo", SupportFileType.JAVA));
        }

        @Test
        void blockCommentEnd_true() {
            assertTrue(CnCharCommentUtil.isComment("foo */", SupportFileType.JAVA));
            assertTrue(CnCharCommentUtil.isComment("*/", SupportFileType.JAVA));
        }

        @Test
        void code_false() {
            assertFalse(CnCharCommentUtil.isComment("int x = 1;", SupportFileType.JAVA));
        }

        @Test
        @DisplayName("前导空白被 trim 后再判定")
        void leadingWhitespaceTrimmed_true() {
            assertTrue(CnCharCommentUtil.isComment("   // foo", SupportFileType.JAVA));
            assertTrue(CnCharCommentUtil.isComment("\t/* foo", SupportFileType.JAVA));
        }

        @Test
        @DisplayName("纯空白行 trim 后为空 → false")
        void blankLine_false() {
            assertFalse(CnCharCommentUtil.isComment("   ", SupportFileType.JAVA));
        }
    }

    @Nested
    @DisplayName("XML 分支: <!-- 与 -->")
    class Xml {
        @Test
        void commentStart_true() {
            assertTrue(CnCharCommentUtil.isComment("<!-- foo", SupportFileType.XML));
        }

        @Test
        void commentEnd_true() {
            assertTrue(CnCharCommentUtil.isComment("foo -->", SupportFileType.XML));
        }

        @Test
        void fullComment_true() {
            assertTrue(CnCharCommentUtil.isComment("<!-- foo -->", SupportFileType.XML));
        }

        @Test
        void element_false() {
            assertFalse(CnCharCommentUtil.isComment("<root>", SupportFileType.XML));
        }
    }

    @Nested
    @DisplayName("SQL 分支: -- 与 /* */")
    class Sql {
        @Test
        void dashComment_true() {
            assertTrue(CnCharCommentUtil.isComment("-- foo", SupportFileType.SQL));
        }

        @Test
        void blockCommentStart_true() {
            assertTrue(CnCharCommentUtil.isComment("/* foo", SupportFileType.SQL));
        }

        @Test
        void blockCommentEnd_true() {
            assertTrue(CnCharCommentUtil.isComment("foo */", SupportFileType.SQL));
        }

        @Test
        void statement_false() {
            assertFalse(CnCharCommentUtil.isComment("SELECT 1", SupportFileType.SQL));
        }
    }

    @Nested
    @DisplayName("GIT_IGNORE 分支: #（无 end 标记）")
    class GitIgnore {
        @Test
        void hashComment_true() {
            assertTrue(CnCharCommentUtil.isComment("# foo", SupportFileType.GIT_IGNORE));
        }

        @Test
        void nonComment_false() {
            assertFalse(CnCharCommentUtil.isComment("build/", SupportFileType.GIT_IGNORE));
        }
    }

    @Nested
    @DisplayName("C/C++ 家族: // 与 /* */（与 JAVA 同标记）")
    class CppFamily {
        @Test
        void cppLineComment_true() {
            assertTrue(CnCharCommentUtil.isComment("// foo", SupportFileType.CPP));
            assertTrue(CnCharCommentUtil.isComment("/* foo", SupportFileType.CPP));
            assertTrue(CnCharCommentUtil.isComment("foo */", SupportFileType.CPP));
            assertFalse(CnCharCommentUtil.isComment("int x;", SupportFileType.CPP));
        }

        @Test
        void allCppFamilyShareMarkers() {
            SupportFileType[] family = {
                    SupportFileType.CPP, SupportFileType.CC, SupportFileType.CXX,
                    SupportFileType.CPLUSPLUS, SupportFileType.C, SupportFileType.H,
                    SupportFileType.HPP, SupportFileType.HH, SupportFileType.HXX,
                    SupportFileType.HPLUSPLUS, SupportFileType.TPP, SupportFileType.INL,
                    SupportFileType.IPP
            };
            for (SupportFileType t : family) {
                assertTrue(CnCharCommentUtil.isComment("// c", t), t + " 应识别 // 注释");
                assertTrue(CnCharCommentUtil.isComment("/* c", t), t + " 应识别 /* 注释");
                assertFalse(CnCharCommentUtil.isComment("code();", t), t + " 不应把代码判为注释");
            }
        }
    }

    @Nested
    @DisplayName("JS/TS/TSX 家族: // 与 /* */")
    class JsFamily {
        @Test
        void jsTsTsxShareMarkers() {
            for (SupportFileType t : new SupportFileType[]{
                    SupportFileType.JS, SupportFileType.TS, SupportFileType.TSX}) {
                assertTrue(CnCharCommentUtil.isComment("// c", t), t + " 应识别 // 注释");
                assertTrue(CnCharCommentUtil.isComment("/* c", t), t + " 应识别 /* 注释");
                assertTrue(CnCharCommentUtil.isComment("c */", t), t + " 应识别 */ 结尾");
                assertFalse(CnCharCommentUtil.isComment("let x = 1;", t), t + " 不应把代码判为注释");
            }
        }
    }

    @Nested
    @DisplayName("现状 BUG 特征化：HTML/CSS/PROPERTIES 未登记注释标记 → 恒 false")
    class CurrentBugUnregisteredMarkers {
        // 说明：这些扩展名虽在 SupportFileType 中“被支持”，但 CnCharCommentUtil 的
        // COMMENT_START_MAP 未给它们登记任何注释标记，导致 commentStartFlags == null，
        // isComment 恒返回 false —— 即这些文件里的注释行不会被识别为注释（会被当作可替换区）。
        // 这是当前行为（bug），此处如实锁定，后续阶段修复时该断言将作为“有意的行为变更”翻转。

        @Test
        @DisplayName("HTML: <!-- 注释行当前 → false（bug）")
        void htmlComment_currentlyFalse() {
            assertFalse(CnCharCommentUtil.isComment("<!-- foo -->", SupportFileType.HTML));
            assertFalse(CnCharCommentUtil.isComment("<!-- foo", SupportFileType.HTML));
        }

        @Test
        @DisplayName("CSS: /* 注释行当前 → false（bug）")
        void cssComment_currentlyFalse() {
            assertFalse(CnCharCommentUtil.isComment("/* foo */", SupportFileType.CSS));
            assertFalse(CnCharCommentUtil.isComment("/* foo", SupportFileType.CSS));
        }

        @Test
        @DisplayName("PROPERTIES: # 注释行当前 → false（bug）")
        void propertiesComment_currentlyFalse() {
            assertFalse(CnCharCommentUtil.isComment("# foo", SupportFileType.PROPERTIES));
            assertFalse(CnCharCommentUtil.isComment("key=value", SupportFileType.PROPERTIES));
        }
    }
}
