package com.haojiyou.cnchar.core;

import com.haojiyou.cnchar.convert.CharConverter;
import com.haojiyou.cnchar.region.InputRegion;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.haojiyou.cnchar.settings.MappingRule;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段3 集成测试：替换执行器 {@link ReplacementExecutor}（需真实 Editor/Document，走平台 fixture）。
 *
 * <p>测试计划 R51 要求覆盖：一对多替换后光标位置、多字符尾部匹配、撤销合并。本类在
 * {@link LightPlatformCodeInsightFixtureTestCase} 引导的真实 {@code Editor/Document} 上，
 * 以「字符已由 typed handler 插入、光标位于其后」的运行期状态构造 {@link EditorContext}，
 * 直接调用 {@link ReplacementExecutor#execute} 验证替换文本、光标（平台托管平移）与推迟重校验：
 * 大部分用例走 defer=false 立即入口；「替换脱离键入命令、独立成步」的撤销分组语义由判别性用例
 * {@link #testDeferredReplacementRunsAsIndependentCommandAfterTypingCommandEnds()} 以 defer=true
 * 经模拟键入命令包裹 + 泵 EDT 直接覆盖（defer=false 在测试线程立即执行、不在任何命令内，
 * 对“键入命令内是否暂存、命令名/groupId 是否正确”无判别力）。
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
 *       一对多替换（…→...）后光标由平台 caret marker 自动平移到替换串末尾（含行中布局变体——
 *       全部在文档末尾的旧用例曾被 moveToOffset 截断到 textLength 的巧合掩盖缺陷，
 *       行中变体是防回归护栏）、多字符尾部匹配（前文恰为匹配键时长度正确）、
 *       无候选/无映射时文档不变、推迟重校验（目标字符被改动 → 静默放弃；文档删减后 marker 自动平移
 *       → 替换仍执行；用户已继续键入 → 替换仍执行且光标随平台平移 delta）、editor 已释放 → 静默放弃，
 *       以及「替换脱离键入命令、以独立撤销命令（名称=替换中文标点）执行且一步可撤销」的判别性验证；
 *       ADAPTIVE 语境窗口提取边界（行中/行首/换行/16 字符截断/不跨行，自 EditorContextTest 迁入——
 *       IC-2020.3 的 {@code DocumentImpl.getText()} 内部走 {@code ReadAction.compute}，
 *       脱平台单测无 Application 必 NPE，故依赖本类 fixture 真实文档）。</li>
 *   <li>撤销分组语义的判别方式：defer=false 立即执行对撤销分组无判别力（无键入命令包裹，修复前也绿）；
 *       判别性用例经 {@code CommandProcessor.executeCommand("typing")} 包裹 defer=true 的 execute，
 *       命令内断言只暂存不写入，泵 EDT 后断言 commandStarted 单独收到且命令名为“替换中文标点”、
 *       UndoManager 一步撤销精确回退。字符插入本身由 typed handler 链完成，本类仍以「已插入」状态起步。</li>
 *   <li>受平台限制未直接触发：<b>HintService 提示</b>（fixture 不加载本插件 plugin.xml，服务未注册，
 *       且所有用例均在 {@code showHint=false} 下运行）与 <b>project.isDisposed 分支</b>（构造 disposed
 *       project 会破坏 fixture 生命周期；editor disposed 分支已由独立 {@link EditorFactory} editor 覆盖，
 *       两者在 canApply 中为同一 if 判定）。</li>
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
        ReplacementExecutor.execute(ctx, converterOf(snapshot), snapshot, false);
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

    /**
     * 行中布局变体（修复项1 护栏）：替换点后方有文本，等长替换 delta=0 平台不平移，
     * 光标保持原位（文档末尾用例的断言曾被 clamp 截断巧合掩盖，本变体锁死行中语义）。
     */
    public void testSingleCharReplaceMidLineCaretUnchanged() {
        // "abc，zz"：a0 b1 c2 ，3 z4 z5；光标 offset=4（，与 zz 之间）
        myFixture.configureByText("a.txt", "abc\uFF0C<caret>zz");
        runExecutor('\uFF0C', defaultSnapshot());

        Editor editor = myFixture.getEditor();
        assertEquals("全角逗号应替换为半角逗号，后方 zz 不受影响", "abc,zz", editor.getDocument().getText());
        assertEquals("等长行中替换 delta=0，光标保持原位 4", 4, editor.getCaretModel().getOffset());
    }

    // ---- P0 回归：ADAPTIVE 语境窗口必须剔除刚键入字符 ----

    /**
     * 端到端回归（真实 Document + EditorContext 生产构造路径）：前文 "hello" 时键入 '。'，
     * 此刻文档为 "hello。"、光标 offset=6。修复前窗口含键入字符（"hello。"），键入的标点把上下文
     * 自污染成中文语境 → PolicyEngine 静默 SKIP；修复后窗口为 "hello"（窗口终点 offset-1）
     * → 英文语境 REPLACE，且真实执行替换。
     *
     * <p>走完整生产管线：{@link RegionClassifier#classify} 判区 → {@link PolicyEngine#decide} 决策 →
     * {@link ReplacementExecutor#execute} 写入替换，不硬编码区域、不仅断言决策。
     */
    public void testContextWindowExcludesTypedCharInAdaptiveRegion() {
        // "hello。"：h0 e1 l2 l3 o4 。5；光标 offset=6，typedChar=中文句号
        myFixture.configureByText("a.txt", "hello\u3002<caret>");
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        EditorContext ctx = new EditorContext(
                getProject(), editor, document, myFixture.getFile(), myFixture.getCaretOffset(), '\u3002');

        assertEquals("语境窗口必须剔除刚键入的句号（窗口终点 offset-1）", "hello", ctx.getTextBeforeCursor());

        // 真实端到端：区域分类 → 策略决策 → 执行替换
        CharAutoReplaceSettings.Snapshot snapshot = defaultSnapshot();
        InputRegion region = RegionClassifier.classify(ctx);
        PolicyEngine.Decision decision = PolicyEngine.decide(region, ctx, snapshot);
        assertEquals("a.txt 为 PLAIN_TEXT（默认 ADAPTIVE）：英文语境键入'。'应 REPLACE（修复前被自污染 SKIP）",
                PolicyEngine.Decision.REPLACE, decision);
        if (decision == PolicyEngine.Decision.REPLACE) {
            ReplacementExecutor.execute(ctx, converterOf(snapshot), snapshot, false);
        }
        assertEquals("ADAPTIVE 英文语境键入'。'应被端到端替换", "hello.", document.getText());
    }

    // ---- EditorContext 语境窗口提取边界（自 EditorContextTest 迁入，fixture 真实文档驱动）----

    /** 行中键入排除键入字符：文档 "hello。" 光标末尾 → 窗口 "hello"。 */
    public void testWindowMidLineTypingExcludesTypedChar() {
        // "hello。"：h0 e1 l2 l3 o4 。5；光标 offset=6
        myFixture.configureByText("a.txt", "hello\u3002<caret>");
        assertEquals("行中键入：窗口必须剔除刚键入的字符", "hello",
                EditorContext.extractTextBefore(
                        myFixture.getEditor().getDocument(), myFixture.getCaretOffset()));
    }

    /** 剔除键入字符后的窗口送入检测器 → 英文语境（P0 自污染回归闭环）。 */
    public void testWindowFeedsDetectorAsEnglishContext() {
        myFixture.configureByText("a.txt", "hello\u3002<caret>");
        String window = EditorContext.extractTextBefore(
                myFixture.getEditor().getDocument(), myFixture.getCaretOffset());
        assertFalse("剔除键入的'。'后窗口为纯英文，不应被判为中文语境",
                CjkContextDetector.isChineseContext(window));
    }

    /** 中文前文仍可识别：文档 "你好。" 光标末尾 → 窗口 "你好" 且判为中文语境。 */
    public void testWindowChinesePrefixStillDetected() {
        // "你好。"：你0 好1 。2；光标 offset=3
        myFixture.configureByText("a.txt", "你好\u3002<caret>");
        String window = EditorContext.extractTextBefore(
                myFixture.getEditor().getDocument(), myFixture.getCaretOffset());
        assertEquals("中文前文仍可识别：窗口应为键入字符前的中文", "你好", window);
        assertTrue("中文前文应被判为中文语境（不替换方向）",
                CjkContextDetector.isChineseContext(window));
    }

    /** 行首键入 → 空窗口（仅断言窗口内容，不掺入决策语义）。 */
    public void testWindowTypingAtLineStartYieldsEmpty() {
        // "ab\nx"：a0 b1 \n2 x3；光标 offset=4，键入的 x 为第 2 行首字符
        myFixture.configureByText("a.txt", "ab\nx<caret>");
        assertEquals("行首键入 → 空窗口（不跨行取更早的行）", "",
                EditorContext.extractTextBefore(
                        myFixture.getEditor().getDocument(), myFixture.getCaretOffset()));
    }

    /** 键入换行符 → 窗口为上一行行尾内容（IC-2020.3 行分隔符归前行，不含换行符）。 */
    public void testWindowTypedNewlineYieldsPreviousLineTail() {
        // "ab\n"：a0 b1 \n2；光标 offset=3；窗口 = 换行符之前同一行内容 "ab"
        myFixture.configureByText("a.txt", "ab\n<caret>");
        assertEquals("键入换行符 → 窗口为上一行行尾内容（≤16 字符，不含换行符）", "ab",
                EditorContext.extractTextBefore(
                        myFixture.getEditor().getDocument(), myFixture.getCaretOffset()));
    }

    /** 16 字符 lookback 截断：长行中键入 → 窗口恰为键入字符前 16 字符。 */
    public void testWindowLookbackCappedAtSixteen() {
        // 20 个 ASCII 字母后键入'。'：窗口 = 键入字符前 16 字符（下标 4..19）
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            text.append((char) ('a' + i));
        }
        text.append('\u3002');
        // "abcdefghijklmnopqrst。"：'。' 位于下标 20，光标 offset=21
        myFixture.configureByText("a.txt", text.toString() + "<caret>");
        assertEquals("16 字符 lookback 截断：窗口恰为键入字符前 16 字符", "efghijklmnopqrst",
                EditorContext.extractTextBefore(
                        myFixture.getEditor().getDocument(), myFixture.getCaretOffset()));
    }

    /** 回溯不跨行：多行长行中键入 → 窗口不越过本行行首、不含上一行内容。 */
    public void testWindowLookbackNeverCrossesLineStart() {
        // 第 2 行 20 个数字字符 + 键入'。'：窗口应为本行内其前 16 字符，不含第 1 行的 "ab" 与换行符
        StringBuilder text = new StringBuilder("ab\n");
        for (int i = 0; i < 20; i++) {
            text.append((char) ('0' + i % 10));
        }
        text.append('\u3002');
        // "ab\n01234567890123456789。"：第 2 行起点 offset=3，'。' 位于下标 23，光标 offset=24
        myFixture.configureByText("a.txt", text.toString() + "<caret>");
        String window = EditorContext.extractTextBefore(
                myFixture.getEditor().getDocument(), myFixture.getCaretOffset());
        assertEquals("回溯不跨行：窗口为本行内键入字符前 16 字符", "4567890123456789", window);
        assertFalse("窗口不得跨行包含换行符", window.contains("\n"));
        assertFalse("窗口不得包含上一行内容", window.startsWith("ab"));
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

    /**
     * 行中布局变体（修复项1 护栏）：一对多替换后光标必须落在替换串末尾——caret 恰在区间终点，
     * 平台按 caret marker 自动平移 delta；后方 zz 保持完好。
     */
    public void testOneToManyReplaceMidLineCaretAtReplacementEnd() {
        // "abc…zz"：a0 b1 c2 …3 z4 z5；光标 offset=4（… 与 zz 之间）
        myFixture.configureByText("a.txt", "abc\u2026<caret>zz");
        runExecutor('\u2026', defaultSnapshot());

        Editor editor = myFixture.getEditor();
        assertEquals("… 应替换为 ...，后方 zz 不受影响", "abc...zz", editor.getDocument().getText());
        assertEquals("行中替换后光标应落在替换串末尾（3 + 3 = 6）", 6,
                editor.getCaretModel().getOffset());
    }

    /**
     * 行中布局变体（修复项1 护栏反证）：光标不得停留在原 offset（若未平移会卡进替换串中间）。
     */
    public void testOneToManyReplaceMidLineNotStuckAtOriginalOffset() {
        // "ab…zzz"：a0 b1 …2 z3 z4 z5；光标 offset=3
        myFixture.configureByText("a.txt", "ab\u2026<caret>zzz");
        runExecutor('\u2026', defaultSnapshot());

        Editor editor = myFixture.getEditor();
        assertEquals("ab...zzz", editor.getDocument().getText());
        assertEquals("行中替换后光标应在替换串末尾 5（2 + 3），而非原 offset 3", 5,
                editor.getCaretModel().getOffset());
    }

    // ---- 撤销分组：execute 的替换是单一 WriteCommandAction 撤销步 ----

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

    /**
     * 判别性验证「替换脱离键入命令、独立成步」（defer=false 对撤销分组无判别力的缺口补齐）：
     * <ol>
     *   <li>用 {@code CommandProcessor.executeCommand(getProject(), ..., "typing", document)} 模拟平台
     *       键入命令包裹 defer=true 的 execute——命令期间应只暂存、不写入文档（此刻 WriteCommandAction
     *       会被嵌套内联进键入命令，这正是生产路径必须推迟的原因）；</li>
     *   <li>命令结束后泵 EDT：invokeLater 的 apply 经 TransactionGuard 包装为独立 write-safe 事务执行，
     *       应单独收到 commandStarted 事件且 {@code getCommandName()} 恰为“替换中文标点”，文档完成替换；</li>
     *   <li>UndoManager 一步撤销精确回退到替换前文本（替换自成撤销步、不并回键入组）。</li>
     * </ol>
     * CommandListener 经 message bus 同步派发，commandStarted 计数在 fixture 下可行
     * （已由 {@link #testReplacementRunsInExactlyOneCommand()} 实证）。
     */
    public void testDeferredReplacementRunsAsIndependentCommandAfterTypingCommandEnds() {
        myFixture.configureByText("a.txt", "abc\u2026<caret>");
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        CharAutoReplaceSettings.Snapshot snapshot = defaultSnapshot();

        List<String> startedCommands = new ArrayList<>();
        CommandListener listener = new CommandListener() {
            @Override
            public void commandStarted(CommandEvent event) {
                startedCommands.add(event.getCommandName());
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

        EditorContext ctx = new EditorContext(getProject(), editor, document, myFixture.getFile(),
                myFixture.getCaretOffset(), '\u2026');
        // 模拟键入命令包裹 defer=true：调度阶段只暂存，命令内不得写入
        CommandProcessor.getInstance().executeCommand(getProject(),
                () -> ReplacementExecutor.execute(ctx, converterOf(snapshot), snapshot, true),
                "typing", document);
        assertEquals("键入命令内只暂存不写入：命令期间文档保持 '…'", "abc\u2026", document.getText());
        assertTrue("模拟键入命令应已启动", startedCommands.contains("typing"));

        // 命令结束后泵 EDT：apply 以独立 write-safe 事务执行、创建独立撤销命令
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals("泵后替换应已完成", "abc...", document.getText());
        assertTrue("替换应以独立撤销命令执行（commandStarted.getCommandName()=替换中文标点）",
                startedCommands.contains("替换中文标点"));

        VirtualFile virtualFile = myFixture.getFile().getVirtualFile();
        myFixture.openFileInEditor(virtualFile);
        FileEditor fileEditor = FileEditorManager.getInstance(getProject()).getSelectedEditor(virtualFile);
        assertNotNull("文件已在编辑器中打开，选中 FileEditor 不应为 null", fileEditor);
        UndoManager undoManager = UndoManager.getInstance(getProject());
        assertTrue("替换应作为可撤销命令记录", undoManager.isUndoAvailable(fileEditor));
        undoManager.undo(fileEditor);
        assertEquals("Ctrl+Z 一步应精确回退到替换前的 …（替换自成撤销步，不并回键入组）",
                "abc\u2026", document.getText());
    }

    /**
     * disposed 分支：editor 已释放后 apply → 静默放弃，文档不变。
     * 用 {@link EditorFactory} 独立创建/释放 editor 构造 disposed 态（不触碰 fixture 自身的 editor，
     * 避免 teardown 双重释放）；project 沿用 fixture 的活 project——构造 disposed project 会破坏
     * fixture 生命周期，而 canApply 中 project/editor 为同一 if 判定，editor 分支已覆盖该路径。
     */
    public void testApplyAbortsSilentlyWhenEditorDisposed() {
        Document document = EditorFactory.getInstance().createDocument("abc\uFF0C");
        Editor editor = EditorFactory.getInstance().createEditor(document, getProject());
        try {
            EditorContext ctx = new EditorContext(getProject(), editor, document, null, 4, '\uFF0C');
            ReplacementExecutor.PendingReplacement pending =
                    ReplacementExecutor.compute(ctx, converterOf(defaultSnapshot()), defaultSnapshot());
            assertNotNull("候选字符应产生待替换项", pending);

            EditorFactory.getInstance().releaseEditor(editor);
            assertTrue("前置条件：editor 应处于 disposed 态", editor.isDisposed());

            ReplacementExecutor.apply(ctx, pending, defaultSnapshot());
            assertEquals("editor 已释放，apply 必须静默放弃，文档不得被改写", "abc\uFF0C", document.getText());
        } finally {
            if (!editor.isDisposed()) {
                EditorFactory.getInstance().releaseEditor(editor);
            }
        }
    }

    // ---- 推迟重校验：场景失效时静默放弃，不污染撤销栈 ----

    /**
     * 推迟重校验（字符被改动）：compute 之后、apply 之前目标区间字符被用户改动 → 放弃替换。
     * 模拟 IME 乱序等时序间隙中「待替换字符已非暂存时原字符」的场景（校验不可省的核心依据）。
     */
    public void testApplyAbortsWhenRegionContentChanged() {
        myFixture.configureByText("a.txt", "abc\uFF0C<caret>");
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        CharAutoReplaceSettings.Snapshot snapshot = defaultSnapshot();

        // 模拟 charTyped 时刻：只读 compute（此时 '，' 仍在 [3,4)）
        EditorContext ctx = new EditorContext(getProject(), editor, document, myFixture.getFile(),
                myFixture.getCaretOffset(), '\uFF0C');
        ReplacementExecutor.PendingReplacement pending =
                ReplacementExecutor.compute(ctx, converterOf(snapshot), snapshot);
        assertNotNull("候选字符应产生待替换项", pending);

        // 模拟推迟期间字符被改动：'，' → 'x'
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(3, 4, "x"));

        ReplacementExecutor.apply(ctx, pending, snapshot);
        assertEquals("目标字符已被改动，重校验必须放弃替换（文档保持用户改动后的样子）",
                "abcx", document.getText());
    }

    /**
     * RangeMarker 平移语义（marker 承载区间的核心价值）：compute 之后文档被删减——
     * 修复前以裸 offset 暂存会判“区间越界”而静默漏替换；改用 marker 后实时区间随删除自动平移，
     * 区间内容仍为原文 → 重校验通过、替换照常执行（同批多 pending 场景同理：首个 delta≠0 替换后，
     * 后续 pending 的 marker 已自动平移，不再因裸偏移过期而漏替换）。
     * <p>原用例 {@code testApplyAbortsWhenDocumentShrunkBelowRange} 的“越界放弃”断言随 RangeMarker
     * 改造失去前提（marker 自动平移使裸 offset 越界场景不复存在），按新语义改写为本用例。
     */
    public void testMarkerFollowsDocumentShrinkAndReplacementStillApplies() {
        myFixture.configureByText("a.txt", "abc\uFF0C<caret>");
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        CharAutoReplaceSettings.Snapshot snapshot = defaultSnapshot();

        EditorContext ctx = new EditorContext(getProject(), editor, document, myFixture.getFile(),
                myFixture.getCaretOffset(), '\uFF0C');
        ReplacementExecutor.PendingReplacement pending =
                ReplacementExecutor.compute(ctx, converterOf(snapshot), snapshot);
        assertNotNull("候选字符应产生待替换项", pending);
        assertEquals("暂存区间应为 [3, 4)", 3, pending.getReplaceStart());
        assertEquals("暂存区间终点应为 4", 4, pending.getReplaceEnd());

        // 模拟推迟期间文档被删减：'abc' 被删，裸 offset [3,4) 已越界，marker 自动平移为 [0,1)
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(0, 3));

        ReplacementExecutor.apply(ctx, pending, snapshot);
        assertEquals("marker 实时区间内容仍为 '，'，替换仍应执行（裸 offset 越界不再漏替换）",
                ",", document.getText());
    }

    /**
     * 推迟期间用户已继续键入（区间内容未变 → 替换仍执行）且替换长度变化：光标同步平移 delta。
     */
    public void testApplyAfterUserContinuedTypingShiftsCaretForLengthChange() {
        myFixture.configureByText("a.txt", "abc\u2026<caret>");
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        CharAutoReplaceSettings.Snapshot snapshot = defaultSnapshot();

        EditorContext ctx = new EditorContext(getProject(), editor, document, myFixture.getFile(),
                myFixture.getCaretOffset(), '\u2026');
        ReplacementExecutor.PendingReplacement pending =
                ReplacementExecutor.compute(ctx, converterOf(snapshot), snapshot);
        assertNotNull("候选字符应产生待替换项", pending);

        // 模拟推迟期间用户继续键入 'x'：文档 "abc…x"，caret→5
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            document.insertString(4, "x");
            editor.getCaretModel().moveToOffset(5);
        });

        ReplacementExecutor.apply(ctx, pending, snapshot);
        assertEquals("区间内容未变（'…' 仍在原位），推迟替换仍应执行", "abc...x", document.getText());
        assertEquals("替换使后方文本平移 +2，已继续键入的光标应同步平移（5+2=7）", 7,
                editor.getCaretModel().getOffset());
    }

    /**
     * 行中布局 + 推迟期间已续打（修复项1 判别性护栏）：替换点后方已有文本 x，替换使后方文本
     * 平移 +2，光标由平台 caret marker 自动平移 delta——修复前手动 repositionCaret 再 +delta
     * 与平台平移叠加成双重偏移（9 被 clamp 到文本长度 8），本用例修复前翻红。
     */
    public void testApplyAfterUserContinuedTypingMidLineShiftsCaretForLengthChange() {
        myFixture.configureByText("a.txt", "abc\u2026<caret>x");
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        CharAutoReplaceSettings.Snapshot snapshot = defaultSnapshot();

        EditorContext ctx = new EditorContext(getProject(), editor, document, myFixture.getFile(),
                myFixture.getCaretOffset(), '\u2026');
        ReplacementExecutor.PendingReplacement pending =
                ReplacementExecutor.compute(ctx, converterOf(snapshot), snapshot);
        assertNotNull("候选字符应产生待替换项", pending);

        // 模拟推迟期间用户继续键入 'y'（替换点后方已有 x）：文档 "abc…yx"，caret→5
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            document.insertString(4, "y");
            editor.getCaretModel().moveToOffset(5);
        });

        ReplacementExecutor.apply(ctx, pending, snapshot);
        assertEquals("区间内容未变（'…' 仍在原位），推迟替换仍应执行", "abc...yx", document.getText());
        assertEquals("后方文本平移 +2，光标应由平台自动平移到 7（修复前手动再 +2 错位到 8）", 7,
                editor.getCaretModel().getOffset());
    }

    /**
     * 等长替换 + 用户已继续键入：文档相对位置不受影响，光标保持用户键入后的位置（不被拉回替换串内）。
     */
    public void testEqualLengthReplaceAfterUserContinuedTypingKeepsCaret() {
        myFixture.configureByText("a.txt", "abc\uFF0C<caret>");
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        CharAutoReplaceSettings.Snapshot snapshot = defaultSnapshot();

        EditorContext ctx = new EditorContext(getProject(), editor, document, myFixture.getFile(),
                myFixture.getCaretOffset(), '\uFF0C');
        ReplacementExecutor.PendingReplacement pending =
                ReplacementExecutor.compute(ctx, converterOf(snapshot), snapshot);
        assertNotNull("候选字符应产生待替换项", pending);

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            document.insertString(4, "x");
            editor.getCaretModel().moveToOffset(5);
        });

        ReplacementExecutor.apply(ctx, pending, snapshot);
        assertEquals("等长替换仍应执行", "abc,x", document.getText());
        assertEquals("等长替换不引起偏移，光标保持用户键入后的位置", 5,
                editor.getCaretModel().getOffset());
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

    /**
     * 行中布局变体（修复项1 护栏）：尾部匹配命中键位于行中、后方有文本——仅替换命中键，
     * 前缀与后方字符均保留，光标经平台平移落在替换串末尾（区间终点与 caret 重合 → 平移 delta=-1）。
     */
    public void testMultiCharTailMatchMidLineKeepsUnrelatedPrefix() {
        CharAutoReplaceSettings.Snapshot snapshot = snapshotWithCustom("ab", "Z");
        // "xyabzz"：x0 y1 a2 b3 z4 z5；光标 offset=4，typedChar='b'（走尾部匹配）
        myFixture.configureByText("a.txt", "xyab<caret>zz");
        runExecutor('b', snapshot);

        Editor editor = myFixture.getEditor();
        assertEquals("仅替换命中键 'ab'→'Z'，前缀 'xy' 与后方 'zz' 均保留",
                "xyZzz", editor.getDocument().getText());
        assertEquals("行中替换后光标应落在替换串末尾（2 + 1 = 3）", 3,
                editor.getCaretModel().getOffset());
    }
}
