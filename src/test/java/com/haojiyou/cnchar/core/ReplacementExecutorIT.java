package com.haojiyou.cnchar.core;

import com.haojiyou.cnchar.convert.CharConverter;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.haojiyou.cnchar.settings.MappingRule;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段3 集成测试：替换执行器 {@link ReplacementExecutor}（需真实 Editor/Document，走平台 fixture）。
 *
 * <p>测试计划 R51 要求覆盖：一对多替换后光标位置、多字符尾部匹配、撤销合并。本类在
 * {@link LightPlatformCodeInsightFixtureTestCase} 引导的真实 {@code Editor/Document} 上，
 * 以「字符已由 typed handler 插入、光标位于其后」的运行期状态构造 {@link EditorContext}，
 * 直接调用 {@link ReplacementExecutor#execute} 验证替换文本、光标重算与单步撤销。
 *
 * <p><b>运行前置（务必阅读）</b>：本类为平台 fixture 测试（JUnit3 风格，经 junit-vintage 引擎随
 * {@code useJUnitPlatform()} 运行），普通 {@code gradlew test} 会连带执行本类；首次运行需联网拉取
 * ideaIC-2020.3 完整发行包（数百 MB）并以 headless 模式引导平台。如需快速运行纯逻辑单测，
 * 可用 {@code --tests} 过滤（例如 {@code gradlew test --tests "com.haojiyou.cnchar.rule.*"
 * --tests "com.haojiyou.cnchar.convert.*" --tests "com.haojiyou.cnchar.settings.*"}）。
 *
 * <p><b>覆盖范围与平台限制（务必阅读）</b>：
 * <ul>
 *   <li>已覆盖：等长单字符替换（全角，→半角；表意空格 U+3000→半角空格，执行级守护）、
 *       一对多替换（…→...）后光标置于替换串末尾、
 *       多字符尾部匹配（前文恰为匹配键时长度正确）、无候选/无映射时文档不变、
 *       替换并入单一 {@code WriteCommandAction} 撤销步（一步可回退到替换前文本）。</li>
 *   <li>受平台限制未直接触发：<b>HintService 提示</b>。{@code ReplacementExecutor.showHint} 依赖
 *       {@code ApplicationManager.getService(HintService.class)}，而纯平台 fixture 不加载本插件的
 *       plugin.xml，服务未注册；且生产代码用 {@code showHint=false} 默认关闭提示，故所有用例均在
 *       {@code showHint=false} 下运行，提示分支不被触发（其 HTML 组装纯逻辑已由其它单测覆盖）。</li>
 *   <li>「撤销合并」的完整语义（把 typed handler 的字符插入与本次替换并入同一步）无法在 fixture 中
 *       复现——字符插入由 typed handler 链完成，本类直接以「已插入」状态起步，故仅能验证
 *       「execute 产生的替换是单一可撤销步」。已满足 R51 对撤销不碎片化的核心诉求。</li>
 *   <li>多字符尾部匹配「仅替换命中键、不误删前缀无关字符」由回归防护用例
 *       {@link #testMultiCharTailMatchKeepsUnrelatedPrefix()} 保证（该处曾存在 over-delete 缺陷，已修复）。</li>
 * </ul>
 */
public class ReplacementExecutorIT extends LightPlatformCodeInsightFixtureTestCase {

    /** 默认快照（showHint=false，无自定义映射）。 */
    private static CharAutoReplaceSettings.Snapshot defaultSnapshot() {
        return CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
    }

    /** 携带单条自定义映射的快照。 */
    private static CharAutoReplaceSettings.Snapshot snapshotWithCustom(String from, String to) {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        List<MappingRule> rules = new ArrayList<>();
        rules.add(new MappingRule(from, to));
        state.customMappings = rules;
        return CharAutoReplaceSettings.Snapshot.build(state);
    }

    /** 基于快照的合并映射表构建转换器（与生产 createConverter 等价）。 */
    private static CharConverter converterOf(CharAutoReplaceSettings.Snapshot snapshot) {
        return new CharConverter(snapshot.getCharMap());
    }

    /**
     * 以「typedChar 已插入文档、光标位于其后」的运行期状态构造上下文并执行替换。
     *
     * @param typedChar 本次击键字符（文档中位于 offset-1 处）
     */
    private void runExecutor(char typedChar, CharAutoReplaceSettings.Snapshot snapshot) {
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        int offset = myFixture.getCaretOffset();
        EditorContext ctx = new EditorContext(
                getProject(), editor, document, myFixture.getFile(), offset, typedChar);
        ReplacementExecutor.execute(ctx, converterOf(snapshot), snapshot);
    }

    // ---- 等长单字符替换 + 光标 ----

    public void testSingleCharFullWidthReplaceAndCaret() {
        // "abc，"：a0 b1 c2 ，3；光标在末尾 offset=4，typedChar=全角逗号
        myFixture.configureByText("a.txt", "abc\uFF0C<caret>");
        runExecutor('\uFF0C', defaultSnapshot());

        Document document = myFixture.getEditor().getDocument();
        assertEquals("全角逗号应替换为半角逗号", "abc,", document.getText());
        assertEquals("等长替换后光标应停在替换串末尾（=原光标位）", 4,
                myFixture.getEditor().getCaretModel().getOffset());
    }

    /**
     * 执行级守护：表意空格 U+3000 必须经执行器真实写入文档为半角空格。
     *
     * <p><b>历史缺陷（已修复）</b>：U+3000 曾被 {@code BlankInputRule} 的
     * {@code Character.isWhitespace} 一刀切拒绝在规则链首节点，永远到不了转换引擎——而
     * {@code CharConverterTest} 直接测 {@code convertChar} 全绿，掩盖了该管线级缺陷。
     * 本用例在<b>执行层</b>守护：确保该字符经 {@code ReplacementExecutor.execute} 真实写入文档时被替换。
     */
    public void testIdeographicSpaceReplacedToHalfWidth() {
        // "abc\u3000"：a0 b1 c2 表意空格3；光标 offset=4，typedChar=表意空格（层3 U+3000→' '，delta=0）
        myFixture.configureByText("a.txt", "abc\u3000<caret>");
        runExecutor('\u3000', defaultSnapshot());

        Document document = myFixture.getEditor().getDocument();
        assertEquals("表意空格 U+3000 应替换为半角空格（执行级守护，防止单测全绿而管线被拦的缺陷复发）",
                "abc ", document.getText());
        assertEquals("等长替换后光标应停在替换串末尾（replaceStart 3 + 1 = 4）", 4,
                myFixture.getEditor().getCaretModel().getOffset());
    }

    // ---- 一对多替换（…→...）：光标必须置于替换串末尾 ----

    public void testOneToManyReplaceCaretAtEndOfReplacement() {
        // "abc…"：a0 b1 c2 …3；光标 offset=4，typedChar=省略号（…→... 一对多，delta=+2）
        myFixture.configureByText("a.txt", "abc\u2026<caret>");
        runExecutor('\u2026', defaultSnapshot());

        Editor editor = myFixture.getEditor();
        assertEquals("… 应替换为 ...", "abc...", editor.getDocument().getText());
        assertEquals("一对多后文档长度应增长 2", 6, editor.getDocument().getTextLength());
        assertEquals("光标须重算并置于替换串末尾（replaceStart 3 + 3 = 6）", 6,
                editor.getCaretModel().getOffset());
    }

    public void testOneToManyReplaceCaretNotStuckAtOriginalOffset() {
        // 反证：若沿用原 offset(=4) 光标会错位到 "abc.|" 中，正确应在末尾 6
        myFixture.configureByText("a.txt", "ab\u2026<caret>");
        runExecutor('\u2026', defaultSnapshot());

        Editor editor = myFixture.getEditor();
        assertEquals("ab...", editor.getDocument().getText());
        assertEquals("光标应在末尾 5（replaceStart 2 + 3），而非原 offset 3", 5,
                editor.getCaretModel().getOffset());
    }

    // ---- 撤销合并：execute 的替换并入单一 WriteCommandAction 撤销步 ----

    /**
     * 确定性验证「撤销不碎片化」：execute 的替换恰好在<b>一个</b>命令（{@code WriteCommandAction}）内完成，
     * 即对应撤销栈中的<b>单一步</b>。以 {@link CommandListener} 计数 execute 期间启动的命令数，
     * 避免依赖 203 平台 {@code UndoManager} 仅提供 {@code FileEditor} 重载所带来的 fixture 组装不确定性。
     */
    public void testReplacementRunsInExactlyOneCommand() {
        myFixture.configureByText("a.txt", "abc\u2026<caret>");
        final int[] started = {0};
        CommandListener listener = new CommandListener() {
            @Override
            public void commandStarted(CommandEvent event) {
                started[0]++;
            }

            @Override
            public void commandFinished(CommandEvent event) {
            }

            @Override
            public void undoTransparentActionStarted() {
            }

            @Override
            public void undoTransparentActionFinished() {
            }
        };
        getProject().getMessageBus().connect().subscribe(CommandListener.TOPIC, listener);

        runExecutor('\u2026', defaultSnapshot());

        assertEquals("execute 应把替换并入恰好一个命令（= 单一撤销步，不碎片化）", 1, started[0]);
        assertEquals("命令内确实发生了替换", "abc...", myFixture.getEditor().getDocument().getText());
    }

    /**
     * 真实撤销回退：通过 fixture 打开的 {@link FileEditor} 调 {@code UndoManager.undo}，
     * 一步即精确回退到替换前文本（IC-2020.3 的 {@code UndoManager} 公开 API 仅有 {@code FileEditor} 重载）。
     */
    public void testUndoRevertsReplacementInOneStep() {
        myFixture.configureByText("a.txt", "abc\u2026<caret>");
        // 先执行替换（此时光标位于 <caret>=offset 4，位置正确）
        runExecutor('\u2026', defaultSnapshot());
        Document document = myFixture.getEditor().getDocument();
        assertEquals("替换后应为 abc...", "abc...", document.getText());

        // 替换完成后再打开 FileEditor 用于撤销（栈按 DocumentReference 记录，与打开时机无关）。
        // 注意：IC-2020.3 的 CodeInsightTestFixture.openFileInEditor(VirtualFile) 返回 void
        //（javap 反编译 testFramework.jar 核实；返回 FileEditor 是更高版本平台签名），
        // 故不能从返回值取，须改由 FileEditorManager 按文件获取选中的 FileEditor。
        VirtualFile virtualFile = myFixture.getFile().getVirtualFile();
        myFixture.openFileInEditor(virtualFile);
        FileEditor fileEditor = FileEditorManager.getInstance(getProject()).getSelectedEditor(virtualFile);
        assertNotNull("文件已在编辑器中打开，选中 FileEditor 不应为 null", fileEditor);
        UndoManager undoManager = UndoManager.getInstance(getProject());
        assertTrue("替换应作为可撤销命令记录", undoManager.isUndoAvailable(fileEditor));

        undoManager.undo(fileEditor);
        assertEquals("撤销一步应精确回退到替换前的 …（未碎片化为多步）", "abc\u2026", document.getText());
    }

    // ---- 无候选 / 无映射：文档与光标均不变 ----

    public void testNonConvertibleCharLeavesDocumentUntouched() {
        // typedChar='z' 非候选、无自定义映射 → convertChar 返回 null，且不进入尾部匹配
        myFixture.configureByText("a.txt", "abz<caret>");
        runExecutor('z', defaultSnapshot());

        Editor editor = myFixture.getEditor();
        assertEquals("非候选字符不应触发任何替换", "abz", editor.getDocument().getText());
        assertEquals("光标保持原位", 3, editor.getCaretModel().getOffset());
    }

    // ---- 多字符尾部匹配（前文恰为匹配键，matchLen 正确）----

    public void testMultiCharTailMatchWhenPrefixEqualsKey() {
        // 自定义多字符键 "aa"→"b"；文档 "aa"，光标 offset=2，typedChar='a'（本身不可转换）
        CharAutoReplaceSettings.Snapshot snapshot = snapshotWithCustom("aa", "b");
        myFixture.configureByText("a.txt", "aa<caret>");
        runExecutor('a', snapshot);

        Editor editor = myFixture.getEditor();
        assertEquals("尾部 'aa' 应整体替换为 'b'", "b", editor.getDocument().getText());
        assertEquals("替换后光标置于替换串末尾（replaceStart 0 + 1）", 1,
                editor.getCaretModel().getOffset());
    }

    // ---- 回归防护：多字符尾部匹配仅替换命中键，不误删前缀无关字符 ----

    /**
     * 回归防护：多字符尾部匹配必须<b>仅替换命中的自定义键本身</b>，不得把匹配键之前的无关字符一并删除。
     *
     * <p><b>历史缺陷（已修复）</b>：{@code ReplacementExecutor.execute} 内尾部匹配的 {@code matchLen}
     * 计算曾用 {@code converter.convertTail(key) != null} 作宽判定——该判定在首轮（key=整段窗口）恒为真，
     * 导致 {@code matchLen} 恒等于窗口长度、{@code replaceStart} 左移到窗口起点而 over-delete 前缀。
     *
     * <p><b>修复后</b>：{@code matchLen = conversion.length() - conversion.delta} 精确还原命中键长度。
     *
     * <p><b>用例</b>：自定义键 {@code "ab"→"Z"}，文档 {@code "xyab"}，光标 offset=4，typedChar='b'。
     * 命中键 "ab"（长度 2）→ replaceStart=2，仅替换 offset 2..4，前缀 "xy" 保留。
     * 期望：document={@code "xyZ"}、caret=3。若 over-delete 缺陷复发，结果会退化为 "Z"/caret=1，本用例即失败。
     */
    public void testMultiCharTailMatchKeepsUnrelatedPrefix() {
        CharAutoReplaceSettings.Snapshot snapshot = snapshotWithCustom("ab", "Z");
        // "xyab"：x0 y1 a2 b3；光标 offset=4，typedChar='b'（'b' 本身不可转换，走尾部匹配）
        myFixture.configureByText("a.txt", "xyab<caret>");
        runExecutor('b', snapshot);

        Editor editor = myFixture.getEditor();
        assertEquals("仅替换命中键 'ab'→'Z'，前缀 'xy' 不得被误删",
                "xyZ", editor.getDocument().getText());
        assertEquals("光标置于替换串末尾（replaceStart 2 + 替换串长度 1 = 3）", 3,
                editor.getCaretModel().getOffset());
    }
}
