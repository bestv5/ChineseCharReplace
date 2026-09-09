package com.haojiyou.cnchar.core;

import com.haojiyou.cnchar.region.InputRegion;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单测：{@link RegionClassifier} 中可脱平台验证的两条判据。
 *
 * <p><b>为何是脱平台纯逻辑测试</b>：CONSOLE / CODE / COMMIT 的 {@code classify()} 端到端判定
 * 依赖 {@code Editor} / {@code PsiFile} / {@code FileType} 等平台对象，需 Mockito 打桩；而当前
 * {@code build.gradle} 未声明 mockito 依赖（仅 junit-jupiter + vintage）。按约束不擅自引入新依赖，
 * 故此处聚焦两条<b>无需运行 IDE 即可验证</b>的核心逻辑：
 * <ol>
 *   <li>COMMIT 提交框识别所依赖的 {@link RegionClassifier#COMMIT_MESSAGE_KEY} 的
 *       <b>单例 / 身份比较</b>语义 —— 正是本次修复的回归防线；</li>
 *   <li>PLAIN_TEXT 判据的扩展名匹配纯逻辑 {@link RegionClassifier#isPlainTextExtension(String)}。</li>
 * </ol>
 *
 * <p>{@link Key} 与 {@link UserDataHolderBase} 均为平台<b>纯工具类</b>，构造与存取不触发任何
 * Application/Project 服务，可在无 IDE 环境下直接实例化，用于忠实复现 Key 的身份比较行为。
 */
class RegionClassifierTest {

    /** COMMIT 提交框标记 Key 的语义测试（本次修复的回归核心）。 */
    @Nested
    @DisplayName("COMMIT：UserData Key 单例与身份比较语义")
    class CommitKeySemantics {

        @Test
        @DisplayName("Key 名称与常量保持一致（Git.CommitMessage）")
        void keyNameMatchesConstant() {
            assertNotNull(RegionClassifier.COMMIT_MESSAGE_KEY);
            assertEquals("Git.CommitMessage", RegionClassifier.COMMIT_MESSAGE_KEY.toString());
        }

        @Test
        @DisplayName("单例 Key 写入后可用同一 Key 读回（round-trip 命中）")
        void singletonKeyRoundTrips() {
            UserDataHolderBase holder = new UserDataHolderBase();
            Object marker = new Object();

            holder.putUserData(RegionClassifier.COMMIT_MESSAGE_KEY, marker);

            // 读取方持有同一个单例 Key 引用 → 必然命中，这正是修复后应有的行为
            assertSame(marker, holder.getUserData(RegionClassifier.COMMIT_MESSAGE_KEY));
        }

        @Test
        @DisplayName("回归防线：相同 name 但新建的 Key 读不回（复现修复前 per-call Key.create 的缺陷）")
        void freshlyCreatedKeyWithSameNameMisses() {
            UserDataHolderBase holder = new UserDataHolderBase();
            holder.putUserData(RegionClassifier.COMMIT_MESSAGE_KEY, new Object());

            // 修复前 getCommitUserDataKey() 每次都 Key.create(...) 新建：
            // 因 Key 基于对象身份比较，name 相同也不是同一对象，故 getUserData 必然 miss。
            Key<Object> freshKeyWithSameName = Key.create("Git.CommitMessage");

            assertNotSame(freshKeyWithSameName, RegionClassifier.COMMIT_MESSAGE_KEY,
                    "同名 Key 由 Key.create 分别创建应为不同实例（身份比较）");
            assertNull(holder.getUserData(freshKeyWithSameName),
                    "以新建的同名 Key 读取应返回 null —— 证明修复前 COMMIT UserData 判据永不命中");
        }

        @Test
        @DisplayName("未写入标记时读取为 null（无提交框标记的普通编辑器不误判）")
        void absentMarkerReadsNull() {
            UserDataHolderBase holder = new UserDataHolderBase();
            assertNull(holder.getUserData(RegionClassifier.COMMIT_MESSAGE_KEY));
        }
    }

    /** PLAIN_TEXT 判据：扩展名匹配纯逻辑。 */
    @Nested
    @DisplayName("PLAIN_TEXT：扩展名匹配纯逻辑")
    class PlainTextExtension {

        @Test
        @DisplayName("纯文本扩展名（txt/md/gitignore/text/log）→ 命中")
        void plainTextExtensionsMatch() {
            assertTrue(RegionClassifier.isPlainTextExtension("txt"));
            assertTrue(RegionClassifier.isPlainTextExtension("md"));
            assertTrue(RegionClassifier.isPlainTextExtension("gitignore"));
            assertTrue(RegionClassifier.isPlainTextExtension("text"));
            assertTrue(RegionClassifier.isPlainTextExtension("log"));
        }

        @Test
        @DisplayName("大小写不敏感（TXT/Md/LOG）→ 命中")
        void matchingIsCaseInsensitive() {
            assertTrue(RegionClassifier.isPlainTextExtension("TXT"));
            assertTrue(RegionClassifier.isPlainTextExtension("Md"));
            assertTrue(RegionClassifier.isPlainTextExtension("LOG"));
        }

        @Test
        @DisplayName("代码 / 结构化文件扩展名（java/xml/py/json）→ 非纯文本")
        void codeExtensionsDoNotMatch() {
            assertFalse(RegionClassifier.isPlainTextExtension("java"));
            assertFalse(RegionClassifier.isPlainTextExtension("xml"));
            assertFalse(RegionClassifier.isPlainTextExtension("py"));
            assertFalse(RegionClassifier.isPlainTextExtension("json"));
        }

        @Test
        @DisplayName("null / 空白扩展名 → 非纯文本（守卫）")
        void nullOrBlankExtensionGuard() {
            assertFalse(RegionClassifier.isPlainTextExtension(null));
            assertFalse(RegionClassifier.isPlainTextExtension(""));
            assertFalse(RegionClassifier.isPlainTextExtension("   "));
        }
    }

    /**
     * UNKNOWN 上下文的兜底归类纯逻辑。
     *
     * <p><b>行为变化守护（计划分组 C）</b>：兜底归类由 CODE（默认 ALWAYS，未知也强制替换）
     * 改为 PLAIN_TEXT（默认 ADAPTIVE，按 CJK 语境决策）；psiFile == null 仍为 UNREACHABLE。
     * {@code classify()} 端到端依赖平台 Editor 无法脱平台验证，本组守护其抽取出的纯函数。
     */
    @Nested
    @DisplayName("UNKNOWN 兜底归类：有 PSI → PLAIN_TEXT 自适应，无 PSI → UNREACHABLE")
    class UnknownRegionFallback {

        @Test
        @DisplayName("PSI 存在的 UNKNOWN 上下文 → PLAIN_TEXT（不再强制 CODE）")
        void unknownWithPsiFallsBackToPlainText() {
            assertSame(InputRegion.PLAIN_TEXT, RegionClassifier.regionForUnknownContext(true));
        }

        @Test
        @DisplayName("无 PSI（psiFile == null）→ UNREACHABLE（保持原有行为）")
        void unknownWithoutPsiIsUnreachable() {
            assertSame(InputRegion.UNREACHABLE, RegionClassifier.regionForUnknownContext(false));
        }
    }
}
