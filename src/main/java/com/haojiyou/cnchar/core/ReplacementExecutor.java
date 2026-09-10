package com.haojiyou.cnchar.core;

import com.haojiyou.cnchar.convert.CharConverter;
import com.haojiyou.cnchar.service.HintService;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 描述: 替换执行器 —— 两阶段模型：「只读计算 compute」+「延迟写入 apply」，替换以独立撤销命令执行（单步撤销）。
 *
 * <p><b>撤销粒度（推迟执行）</b>：{@code charTyped}（typedHandler EP）同步发生在平台键入命令
 * 内部——此刻调用 {@link WriteCommandAction} 会被 {@code CoreCommandProcessor} 嵌套内联
 * （{@code myCurrentCommand != null} 时直接执行 runnable、不新建命令），替换会并入用户键入的撤销组；
 * 而键入命令 groupId=Document 且连续键入自动合并，Ctrl+Z 会撤掉整段键入（视觉上整行），
 * 替换无法单独撤销。为此生产入口 {@link #execute(EditorContext, CharConverter, CharAutoReplaceSettings.Snapshot)}
 * 在 {@code charTyped} 内只做只读计算，随后经 {@code ApplicationManager.invokeLater} 推迟到 EDT
 * 稍后执行——invokeLater 的 runnable 由 TransactionGuard 包装为独立 write-safe 事务执行，届时
 * 键入命令已 {@code finishCommand}，写入才会真正创建<b>独立撤销命令</b>（显式命令名、groupId=null
 * 永不与键入组合并）。预期撤销序列：[连续键入组] → [替换] → [后续键入组]。
 * 推迟执行带来时序间隙，{@link #apply} 在写入前必须重校验，场景失效则静默放弃（不打扰用户）。
 *
 * <p><b>核心约定</b>：
 * <ul>
 *   <li>compute（{@link #compute}）：纯只读，产出 {@link PendingReplacement}——待替换区间以
 *       {@link RangeMarker} 承载（选型理由见 {@link #canApply}）+ 区间原文 + 替换串，不写文档、无副作用</li>
 *   <li>apply（{@link #apply}）：以 marker 实时区间重校验 → {@link WriteCommandAction}（4 参重载
 *       {@code runWriteCommandAction(project, name, groupId, runnable)}：命令名"替换中文标点"、
 *       groupId=null）内写入 → 提示（替换成功才提示）→ 释放 marker；命令内再做一次区间复核（防 TOCTOU）</li>
 *   <li>光标策略（平台托管，无手动补偿）：caret 由 {@code CaretImpl$PositionMarker}（RangeMarkerImpl）
 *       承载，replaceString 平移其后方文本时平台自动把 caret 平移 delta（含"恰在区间终点"情形）——
 *       用户未移动 → 落在替换串末尾；已续打 → 跟随用户文本；已移走 → 停在用户位置；多光标逐 caret
 *       独立平移，各自正确。执行器不做任何 moveToOffset——任何手动 +delta 都会与平台自动平移
 *       叠加成双重偏移（实测 IC-2020.3 行为，勿再引入手动重定位）</li>
 *   <li>多字符键兜底匹配：对光标前尾部做一次最长优先匹配。多光标场景由 charTyped 逐 caret 回调、
 *       逐 caret 产出 pending；同批多个 pending 的区间各由自身 marker 承载，前一个 delta≠0 的替换
 *       发生后，后续 marker 已自动平移到真实文本位置，不因裸偏移过期而静默漏替换</li>
 * </ul>
 *
 * @author : best.xu
 */
public final class ReplacementExecutor {

    private static final Logger LOG = Logger.getInstance(ReplacementExecutor.class);

    /** 独立撤销命令的名称（撤销下拉可见，中文展示）。 */
    private static final String UNDO_COMMAND_NAME = "替换中文标点";

    private ReplacementExecutor() {
    }

    /**
     * 生产入口：只读计算后<b>推迟</b>到 EDT 稍后执行替换，使替换脱离键入命令、自成独立撤销步。
     *
     * <p>等价于 {@code execute(ctx, converter, snapshot, true)}。
     */
    public static void execute(@NotNull EditorContext ctx,
                               @NotNull CharConverter converter,
                               @NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        execute(ctx, converter, snapshot, true);
    }

    /**
     * 执行替换。
     *
     * @param defer {@code true} 推迟到 EDT 稍后执行（生产路径，脱离键入撤销组）；
     *              {@code false} 当前线程立即同步执行（测试路径；撤销分组是平台运行时行为，
     *              立即路径无判别力，判别性用例经模拟键入命令 + defer=true 覆盖）
     */
    public static void execute(@NotNull EditorContext ctx,
                               @NotNull CharConverter converter,
                               @NotNull CharAutoReplaceSettings.Snapshot snapshot,
                               boolean defer) {
        PendingReplacement pending = compute(ctx, converter, snapshot);
        if (pending == null) {
            return;
        }
        if (defer) {
            scheduleDeferred(ctx, pending, snapshot);
        } else {
            apply(ctx, pending, snapshot);
        }
    }

    /**
     * 只读计算（charTyped 阶段调用，不写文档）：优先单字符三层转换，未命中且配置了多字符
     * 自定义键时对光标前尾部做最长优先匹配。
     *
     * @return 待替换项；无可替换内容（非候选/无映射）或替换区间非法时返回 {@code null}
     */
    @Nullable
    public static PendingReplacement compute(@NotNull EditorContext ctx,
                                             @NotNull CharConverter converter,
                                             @NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        Document document = ctx.getDocument();
        int offset = ctx.getOffset();
        char typedChar = ctx.getTypedChar();

        CharConverter.Conversion conversion = converter.convertChar(typedChar);

        if (conversion == null && !snapshot.getMultiCharFirstChars().isEmpty()) {
            String beforeCursor = document.getText().substring(Math.max(0, offset - CharConverter.MAX_MULTI_CHAR_LEN), offset);
            conversion = converter.convertTail(beforeCursor);
            if (conversion != null) {
                // convertTail 已按“最长优先”命中确切的多字符自定义键，其 Conversion.delta =
                // 替换串长度 − 命中的键长度（见 CharConverter#convertTail 契约），故命中的实际键长度
                // 可由 length() − delta 精确还原。据此定位 replaceStart，仅替换匹配键本身，
                // 不再左移到窗口起点误删前缀无关字符（修复 over-delete 缺陷）。
                int matchLen = conversion.length() - conversion.delta;
                int replaceStart = offset - matchLen;
                return buildPending(document, replaceStart, offset, conversion, typedChar);
            }
        }

        if (conversion == null) {
            return null;
        }

        return buildPending(document, offset - 1, offset, conversion, typedChar);
    }

    /**
     * 应用待替换项：以 marker 实时区间重校验 → 独立撤销命令内写入 → 提示 → 释放 marker。
     * <b>不做任何手动光标重定位</b>：caret 是 {@code CaretImpl$PositionMarker}（RangeMarkerImpl），
     * replaceString 平移其右侧文本时平台自动平移 caret（见类注释），手动补偿必成双重平移。
     *
     * <p><b>重校验（推迟模型必需，不可省）</b>：推迟执行与 charTyped 之间存在时序间隙，
     * 快速连续键入时 invokeLater 通常先于下一击键执行，但 IME 场景事件可能乱序。执行前校验
     * project/editor 未 disposed、marker 仍有效、marker 实时区间内容仍为暂存时的原文；
     * 任一不满足则静默放弃本次替换（debug 日志，不打扰用户）。
     *
     * <p>重校验置于 {@link WriteCommandAction} 之外：失败时不创建命令，避免污染撤销栈出现空撤销步。
     * 入口假设：本方法在 EDT 上、键入命令之外执行（生产路径由 invokeLater/TransactionGuard 保障）；
     * 入口对"嵌套命令"仅 warn 不中断，便于 runIde 实测暴露假设失效。
     */
    public static void apply(@NotNull EditorContext ctx,
                             @NotNull PendingReplacement pending,
                             @NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        Project project = ctx.getProject();
        Editor editor = ctx.getEditor();
        Document document = ctx.getDocument();

        if (CommandProcessor.getInstance().getCurrentCommand() != null) {
            // 诊断性告警：嵌套进活动命令会把替换并入其撤销组，独立单步撤销假设失效。
            LOG.warn("ReplacementExecutor.apply invoked inside an active command: "
                    + CommandProcessor.getInstance().getCurrentCommand());
        }

        if (!canApply(ctx, pending)) {
            releaseQuietly(pending);
            return;
        }

        RangeMarker marker = pending.getRangeMarker();
        try {
            // 4 参重载：显式命令名（撤销下拉显示中文）、groupId=null（null 永不与键入组或后续键入合并，
            // 替换自成撤销步）。执行到这里时键入命令已 finishCommand，命令真正新建而非嵌套内联。
            WriteCommandAction.runWriteCommandAction(project, UNDO_COMMAND_NAME, null, () -> {
                int start = marker.getStartOffset();
                int end = marker.getEndOffset();
                // 命令内二次复核（TOCTOU 加固）：canApply 与本次写入之间若文档又被改动
                // （apply 被非常规调用栈使用时可能发生），区间越界或内容失配则放弃——
                // runnable 提前 return 不产生文档变更，不会在撤销栈留下空步。
                if (end > document.getTextLength() || start > end
                        || !pending.getOriginalText().contentEquals(
                                document.getCharsSequence().subSequence(start, end))) {
                    LOG.debug("in-command re-validation failed, replacement aborted: " + pending);
                    return;
                }
                document.replaceString(start, end, pending.getReplacementText());

                if (snapshot.isShowHint()) {
                    showHint(editor, String.valueOf(pending.getTypedChar()), pending.getReplacementText());
                }
            });
        } finally {
            releaseQuietly(pending);
        }
    }

    /** 释放 pending 持有的 marker（成功/放弃路径均释放，防泄漏）；文档可能已随 editor 释放，故吞异常。 */
    private static void releaseQuietly(@NotNull PendingReplacement pending) {
        try {
            pending.getRangeMarker().dispose();
        } catch (Exception ignored) {
            // 文档已释放等场景下 dispose 可能失败，忽略
        }
    }

    /**
     * 推迟调度：经 invokeLater 把 apply 派发到 EDT 稍后执行。此刻虽仍在键入命令内排队，但
     * invokeLater 的 runnable 不并入键入命令——TransactionGuard 会将其包装为一次独立、
     * write-safe 的事务执行，届时键入命令已 finishCommand，apply 的 WriteCommandAction
     * 才真正新建独立撤销命令。模态态取编辑器组件所属层级，模态对话框期间不执行替换。
     */
    private static void scheduleDeferred(@NotNull EditorContext ctx,
                                         @NotNull PendingReplacement pending,
                                         @NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        Editor editor = ctx.getEditor();
        ApplicationManager.getApplication().invokeLater(
                () -> apply(ctx, pending, snapshot),
                ModalityState.stateForComponent(editor.getComponent()));
    }

    /**
     * 重校验：project/editor 未 disposed、marker 有效、marker 实时区间落在文档界内且内容仍为暂存时的原文。
     *
     * <p><b>区间以 {@link RangeMarker} 承载（选型理由）</b>：同一事件批（IME 一次上屏多字符逐
     * charTyped 回调、多光标）会产出多个 pending，若以裸 offset 暂存，首个 delta≠0 的替换会使
     * 后续 pending 的绝对偏移整体过期（内容失配 → 静默漏替换）。marker 默认随文档变更自动
     * 伸缩/平移（覆盖区被改写时收缩为空、其右侧文本增删时整体平移），后续 pending 的区间始终
     * 跟随真实文本位置，无需手动补偿；区间内文本被改写时内容与原文失配，重校验照常拦截。
     */
    private static boolean canApply(@NotNull EditorContext ctx, @NotNull PendingReplacement pending) {
        Project project = ctx.getProject();
        Editor editor = ctx.getEditor();
        Document document = ctx.getDocument();

        if (project.isDisposed() || editor.isDisposed()) {
            LOG.debug("deferred replacement aborted: project/editor disposed, pending=" + pending);
            return false;
        }
        RangeMarker marker = pending.getRangeMarker();
        if (!marker.isValid()) {
            LOG.debug("deferred replacement aborted: range marker invalid, pending=" + pending);
            return false;
        }
        int start = marker.getStartOffset();
        int end = marker.getEndOffset();
        if (start < 0 || end > document.getTextLength() || start > end) {
            LOG.debug("deferred replacement aborted: range out of bounds [" + start + ", " + end
                    + "), docLength=" + document.getTextLength());
            return false;
        }
        CharSequence current = document.getCharsSequence().subSequence(start, end);
        if (!pending.getOriginalText().contentEquals(current)) {
            LOG.debug("deferred replacement aborted: region content changed, expected="
                    + pending.getOriginalText());
            return false;
        }
        return true;
    }

    @Nullable
    private static PendingReplacement buildPending(@NotNull Document document,
                                                   int replaceStart,
                                                   int replaceEnd,
                                                   @NotNull CharConverter.Conversion conversion,
                                                   char typedChar) {
        if (replaceStart < 0 || replaceEnd > document.getTextLength() || replaceStart > replaceEnd) {
            return null;
        }
        String originalText = document.getCharsSequence().subSequence(replaceStart, replaceEnd).toString();
        // 区间以 RangeMarker 承载：随文档编辑自动平移（选型理由见 canApply/类注释），apply 终了释放。
        RangeMarker marker = document.createRangeMarker(replaceStart, replaceEnd);
        return new PendingReplacement(marker, conversion.text, originalText, typedChar);
    }

    private static void showHint(@NotNull Editor editor, @NotNull String original, @NotNull String replacement) {
        try {
            HintService hintService = HintService.getInstance();
            String hint = hintService.createHint(original, replacement);
            hintService.showHint(editor, hint, null);
        } catch (Exception e) {
            // 提示失败不影响主流程
        }
    }

    /**
     * 待替换项：compute 的不可变产物，apply 的输入。替换区间由 {@link RangeMarker} 承载
     * （随文档编辑自动平移，同批 pending 互不失效），一次性使用，apply 终了由执行方释放。
     */
    public static final class PendingReplacement {

        /** 承载替换区间的 marker（默认伸缩语义：其右侧文本增删时区间整体平移）。 */
        @NotNull
        private final RangeMarker rangeMarker;
        /** 替换后的字符串（一对多时长度 &gt; 1，如 …→...）。 */
        private final String replacementText;
        /** 暂存时替换区间的原文，推迟执行后的重校验依据。 */
        private final String originalText;
        /** 触发本次替换的键入字符（提示展示用）。 */
        private final char typedChar;

        private PendingReplacement(@NotNull RangeMarker rangeMarker,
                                   @NotNull String replacementText,
                                   @NotNull String originalText,
                                   char typedChar) {
            this.rangeMarker = rangeMarker;
            this.replacementText = replacementText;
            this.originalText = originalText;
            this.typedChar = typedChar;
        }

        /** 替换区间起点（含）：取 marker 实时起点，随文档编辑自动平移。 */
        public int getReplaceStart() {
            return rangeMarker.getStartOffset();
        }

        /** 替换区间终点（不含）：取 marker 实时终点，随文档编辑自动平移。 */
        public int getReplaceEnd() {
            return rangeMarker.getEndOffset();
        }

        @NotNull
        public RangeMarker getRangeMarker() {
            return rangeMarker;
        }

        @NotNull
        public String getReplacementText() {
            return replacementText;
        }

        @NotNull
        public String getOriginalText() {
            return originalText;
        }

        public char getTypedChar() {
            return typedChar;
        }

        @Override
        public String toString() {
            return "PendingReplacement{[" + getReplaceStart() + ", " + getReplaceEnd() + ") '"
                    + originalText + "' → '" + replacementText + "'}";
        }
    }
}
