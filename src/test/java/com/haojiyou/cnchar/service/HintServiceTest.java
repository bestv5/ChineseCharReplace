package com.haojiyou.cnchar.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段3 单测：提示构建 {@link HintService#createHint(String, String)}（纯字符串逻辑、脱平台）。
 *
 * <p>{@code HintService} 有隐式公有无参构造、且静态模板 {@code TIPS} 为普通字符串常量，
 * 故 {@code new HintService()} 不触发任何平台初始化，可在纯 JUnit5 下直接调用 {@code createHint}。
 * 私有方法 {@code escapeHtml / originalHtml / replacement} 的效果均通过 {@code createHint} 的
 * 返回字符串间接断言。
 *
 * <p><b>不在覆盖范围</b>：{@code showHint} / {@code getInstance} 依赖 {@code ApplicationManager}
 * 与 {@code HintManagerImpl}（平台行为），非纯逻辑，本测试不触及。
 *
 * <p>CJK / 特殊字符一律用 Unicode 转义或字面量精确书写，断言以「稳定子串」为主，避免对
 * 全角标点、样式空白等易错细节的脆弱匹配。
 */
class HintServiceTest {

    /** originalHtml 的 &lt;code&gt; 开标签（红色，来自 HintService 源码，逐字复制）。 */
    private static final String ORIG_CODE_OPEN = "<code style=\"color: #ef5149; font-size: 1.5em;\">";
    /** replacement 的 &lt;code&gt; 开标签（绿色，来自 HintService 源码，逐字复制）。 */
    private static final String REPL_CODE_OPEN = "<code style=\"color: #5c962c; font-size: 1.5em;\">";

    private final HintService service = new HintService();

    // ---- 1. 普通字符组装：标题 / 连接语 / <code> 包裹 ----

    @Test
    @DisplayName("createHint 普通字符：含标题、连接语，原字符与替换串均被 <code> 包裹")
    void createHintAssemblesNormalChars() {
        String html = service.createHint("\uFF0C", ","); // ，→,

        assertTrue(html.contains("提示"), "应含标题「提示」");
        assertTrue(html.contains("已被改为"), "应含连接语「已被改为」");
        assertTrue(html.contains("[撤回]"), "应含操作提示中的 [撤回]");

        // 原字符被红色 <code> 包裹、替换串被绿色 <code> 包裹
        assertTrue(html.contains(ORIG_CODE_OPEN + "\uFF0C</code>"),
                "原字符应被红色 <code>…</code> 包裹");
        assertTrue(html.contains(REPL_CODE_OPEN + ",</code>"),
                "替换串应被绿色 <code>…</code> 包裹");

        // HTML 骨架保留
        assertTrue(html.startsWith("<html>"), "结果应以 <html> 开头");
        assertTrue(html.endsWith("</html>"), "结果应以 </html> 结尾");
    }

    // ---- 2. escapeHtml 转义效果（通过 createHint 观测）----

    @Test
    @DisplayName("escapeHtml：& < > \" ' 五种特殊字符转为对应 HTML 实体")
    void escapeHtmlEscapesAllSpecialChars() {
        String html = service.createHint("&<>\"'", "x");

        // & → &amp; 、< → &lt; 、> → &gt; 、" → &quot; 、' → &#39;（按输入顺序拼接）
        assertTrue(html.contains(ORIG_CODE_OPEN + "&amp;&lt;&gt;&quot;&#39;</code>"),
                "五种特殊字符应分别转义为 &amp; &lt; &gt; &quot; &#39;");
        // 转义后原文中的裸特殊字符不应再出现在被包裹的用户内容里
        assertFalse(html.contains(ORIG_CODE_OPEN + "&<>\"'</code>"),
                "特殊字符不应以裸形式保留");
    }

    @Test
    @DisplayName("escapeHtml：& 不产生二次转义（得 &amp; 而非 &amp;amp;）")
    void escapeHtmlDoesNotDoubleEscapeAmpersand() {
        String html = service.createHint("&", "x");

        assertTrue(html.contains(ORIG_CODE_OPEN + "&amp;</code>"),
                "单个 & 应转义为 &amp;");
        assertFalse(html.contains("&amp;amp;"),
                "& 必须只转义一次，不得出现二次转义 &amp;amp;");
    }

    @Test
    @DisplayName("escapeHtml：已含实体的输入按字面再转义（&amp; → &amp;amp;）")
    void escapeHtmlTreatsEntityTextLiterally() {
        // 逐字符转义：输入的字面 "&amp;" 中 '&' 先转义，其余字符原样 → "&amp;amp;"
        String html = service.createHint("&amp;", "x");
        assertTrue(html.contains(ORIG_CODE_OPEN + "&amp;amp;</code>"),
                "字面量 &amp; 的 '&' 应被转义，整体成为 &amp;amp;（证明按字面处理而非解析实体）");
    }

    // ---- 3. 一对多替换串呈现（…→...）----

    @Test
    @DisplayName("一对多：…→... 在提示中原样呈现（点号非特殊字符不转义）")
    void createHintShowsOneToManyReplacement() {
        String html = service.createHint("\u2026", "..."); // …→...

        assertTrue(html.contains(ORIG_CODE_OPEN + "\u2026</code>"),
                "原字符 … 应被 <code> 包裹");
        assertTrue(html.contains(REPL_CODE_OPEN + "...</code>"),
                "一对多替换串 ... 应完整被 <code> 包裹");
        assertTrue(html.contains("已被改为"), "连接语仍存在");
    }

    // ---- 4. null 入参健壮性 ----

    @Test
    @DisplayName("健壮性：createHint(null, null) 不抛异常且骨架完整")
    void createHintWithBothNullIsSafe() {
        String html = assertDoesNotThrow(() -> service.createHint(null, null),
                "null 入参不应抛异常");
        assertTrue(html.contains("已被改为"), "连接语仍应存在");
        assertTrue(html.contains("提示"), "标题仍应存在");
    }

    @Test
    @DisplayName("健壮性：escapeHtml(null) 返回空串（original 为 null → 空 <code></code>）")
    void createHintWithNullOriginalYieldsEmptyCode() {
        String html = service.createHint(null, "x");
        assertTrue(html.contains(ORIG_CODE_OPEN + "</code>"),
                "original=null 时 escapeHtml 应返回空串，得到空的 <code></code>");
        assertTrue(html.contains(REPL_CODE_OPEN + "x</code>"),
                "replacement 正常包裹");
    }

    @Test
    @DisplayName("健壮性：replacement 为 null → 空 <code></code>，不抛异常")
    void createHintWithNullReplacementYieldsEmptyCode() {
        String html = assertDoesNotThrow(() -> service.createHint("&", null));
        assertTrue(html.contains(REPL_CODE_OPEN + "</code>"),
                "replacement=null 时应得到空的 <code></code>");
        assertTrue(html.contains(ORIG_CODE_OPEN + "&amp;</code>"),
                "original 仍正常转义包裹");
    }

    // ---- 5. 模板占位符已被替换 ----

    @Test
    @DisplayName("占位符：结果中不再残留 AutoFix: / CONTENT / TIPS 字面量")
    void createHintReplacesAllTemplatePlaceholders() {
        String html = service.createHint("\uFF0C", ","); // ，→,

        assertFalse(html.contains("AutoFix:"), "占位符 AutoFix: 应被标题替换");
        assertFalse(html.contains("CONTENT"), "占位符 CONTENT 应被内容 span 替换");
        assertFalse(html.contains("TIPS"), "占位符 TIPS 应被操作提示替换");

        // 替换后的实际内容应出现
        assertTrue(html.contains("如果不需要替换"), "应含操作提示正文");
        assertTrue(html.contains("#777777"), "内容 span 的颜色样式应保留");
    }
}
