package com.haojiyou.cnchar.core;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.haojiyou.cnchar.common.StrUtil;
import com.haojiyou.cnchar.region.InputRegion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 描述: 区域分类器 —— 判定输入发生所在的 {@link InputRegion}。
 *
 * <p>分类优先级（从高到低）：
 * <ol>
 *   <li>UNREACHABLE：Terminal、普通 Swing 文本框等 typedHandler 触达不到的区域</li>
 *   <li>CONSOLE：控制台编辑器（{@link EditorKind#CONSOLE}）</li>
 *   <li>COMMIT：Git 提交框（PlainTextLanguage + 无 VirtualFile + CommitMessage UserData）</li>
 *   <li>PLAIN_TEXT：纯文本文件（.txt/.md/.gitignore 等无代码 PSI 的编辑器）</li>
 *   <li>COMMENT / STRING / CODE：由 {@link CommentContextResolver} 判定
 *       （PSI 主判 + SyntaxHighlighter token 兜底 + Commenter 兜底）</li>
 * </ol>
 *
 * <p><b>行为变化（计划分组 C）</b>：PSI 存在但 resolver 返回 UNKNOWN（如 offset 越界无元素）时，
 * 兜底归类由<b> CODE 改为 PLAIN_TEXT 自适应</b>——未知上下文不再强制按 ALWAYS 替换的 CODE 处理，
 * 而是走 PLAIN_TEXT 的 ADAPTIVE 策略由 CJK 语境决策，降低误替换风险；
 * {@code psiFile == null} 仍保持 {@link InputRegion#UNREACHABLE}。
 *
 * <p><b>弃用 {@code getSelectedEditor()}</b>：分屏场景会误判，所有判定均从事件传入的 Editor/PsiFile 派生。
 *
 * @author : best.xu
 */
public final class RegionClassifier {

    private static final String COMMIT_MESSAGE_USER_DATA_KEY = "Git.CommitMessage";

    /**
     * 提交框 UserData 标记 Key —— 必须为<b>单例静态 final 字段</b>（{@code Key.create} 仅调用一次）。
     *
     * <p><b>为何不能每次新建</b>：{@link Key} 采用<b>对象身份</b>（identity）语义 ——
     * {@code UserDataHolderBase} 内部以 {@code System.identityHashCode + ==} 存取，两个 name 相同、
     * 但由 {@code Key.create} 分别创建的实例互不相等。修复前 {@code getCommitUserDataKey()} 每次调用都
     * {@code Key.create(...)} 新建，导致写入方与读取方持有不同 Key 实例，{@code getUserData} 必然返回 null，
     * COMMIT 的 UserData 判据永不命中。
     *
     * <p><b>仍存在的局限（需 runIde 实测）</b>：即便此处使用单例，也只有当写入方持有<b>同一个</b> Key 引用时
     * 才能命中。IC-2020.3 SDK 未提供可稳定引用的公共「提交信息」Key 常量，故本 UserData 分支为尽力而为的
     * 前置快速判据；COMMIT 的实际识别主要依赖 {@link #isCommitMessageEditor} 中的启发式
     * （非 MAIN_EDITOR/CONSOLE 的 EditorKind + 无 VirtualFile + 文件名兜底），最终准确性须在 runIde 中实测确认。
     */
    static final Key<Object> COMMIT_MESSAGE_KEY = Key.create(COMMIT_MESSAGE_USER_DATA_KEY);

    private static final String[] PLAIN_TEXT_EXTENSIONS = {
            "txt", "md", "gitignore", "text", "log"
    };

    private RegionClassifier() {
    }

    /**
     * 判定当前击键所在的语义区域。
     *
     * @param ctx 击键上下文
     * @return 区域枚举
     */
    @NotNull
    public static InputRegion classify(@NotNull EditorContext ctx) {
        Editor editor = ctx.getEditor();
        Project project = ctx.getProject();
        PsiFile psiFile = ctx.getPsiFile();

        if (isConsoleEditor(editor)) {
            return InputRegion.CONSOLE;
        }

        if (isCommitMessageEditor(editor, psiFile, project)) {
            return InputRegion.COMMIT;
        }

        if (isPlainTextFile(psiFile)) {
            return InputRegion.PLAIN_TEXT;
        }

        CommentContextResolver.ContextType contextType =
                CommentContextResolver.resolve(psiFile, ctx.getOffset(), ctx.getDocument(), project, editor);

        switch (contextType) {
            case COMMENT:
                return InputRegion.COMMENT;
            case STRING:
                return InputRegion.STRING;
            case CODE:
                return InputRegion.CODE;
            case UNKNOWN:
            default:
                return regionForUnknownContext(psiFile != null);
        }
    }

    /**
     * 纯逻辑：UNKNOWN 上下文的最终归类（可脱平台单测）。
     *
     * <p><b>行为变化说明（对齐设计）</b>：过去兜底为 {@link InputRegion#CODE}（默认 ALWAYS，
     * 未知上下文一律替换）；现改为 {@link InputRegion#PLAIN_TEXT}（默认 ADAPTIVE，按 CJK 语境决策）。
     * 理由：resolver 返回 UNKNOWN 说明对该位置无任何可用判据（如 offset 越界、无 PSI 元素），
     * 强制 CODE 会在无判据时也执行替换，风险高；PLAIN_TEXT 自适应把决定权交给语境启发式，更安全。
     * 仅当 {@code psiFile == null}（无 PSI 体系，如 commit 框外的特殊编辑器）时才视为
     * {@link InputRegion#UNREACHABLE}（typedHandler 链路无法安全处理）。
     *
     * @param psiFilePresent PSI 文件是否非 null
     * @return PLAIN_TEXT（有 PSI）或 UNREACHABLE（无 PSI）
     */
    static InputRegion regionForUnknownContext(boolean psiFilePresent) {
        return psiFilePresent ? InputRegion.PLAIN_TEXT : InputRegion.UNREACHABLE;
    }

    /**
     * 控制台编辑器判定：{@link EditorKind#CONSOLE}。
     */
    private static boolean isConsoleEditor(@NotNull Editor editor) {
        return editor.getEditorKind() == EditorKind.CONSOLE;
    }

    /**
     * Git 提交框判定：
     * <ul>
     *   <li>非 MAIN_EDITOR 且非 CONSOLE（排除普通编辑器和控制台）</li>
     *   <li>无关联 VirtualFile（非真实文件）</li>
     *   <li>UserData 含 "Git.CommitMessage" 标记，或文件名含 "COMMIT_EDITMSG"</li>
     * </ul>
     */
    private static boolean isCommitMessageEditor(@NotNull Editor editor,
                                                  @Nullable PsiFile psiFile,
                                                  @NotNull Project project) {
        if (editor.getEditorKind() == EditorKind.MAIN_EDITOR
                || editor.getEditorKind() == EditorKind.CONSOLE) {
            return false;
        }
        VirtualFile vf = psiFile != null ? psiFile.getVirtualFile() : null;
        if (vf != null) {
            return false;
        }
        Object commitMarker = editor.getUserData(COMMIT_MESSAGE_KEY);
        if (commitMarker != null) {
            return true;
        }
        if (psiFile != null) {
            String name = psiFile.getName();
            if (name.contains("COMMIT_EDITMSG") || name.contains("commit")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 纯文本文件判定：扩展名在 PLAIN_TEXT_EXTENSIONS 列表中。
     */
    private static boolean isPlainTextFile(@Nullable PsiFile psiFile) {
        if (psiFile == null) {
            return false;
        }
        FileType fileType = psiFile.getFileType();
        if (fileType == null) {
            return false;
        }
        return isPlainTextExtension(fileType.getDefaultExtension());
    }

    /**
     * 纯逻辑：判断扩展名是否属于纯文本类（.txt/.md/.gitignore/.text/.log）。
     *
     * <p>从 {@link #isPlainTextFile} 抽出，脱离 {@code PsiFile}/{@code FileType} 平台依赖，
     * 便于纯单测覆盖 PLAIN_TEXT 判据。null / 空白扩展名视为非纯文本。
     *
     * @param ext 文件默认扩展名（不含点，可为 null）
     * @return true = 纯文本扩展名
     */
    static boolean isPlainTextExtension(@Nullable String ext) {
        if (StrUtil.isBlank(ext)) {
            return false;
        }
        for (String plainExt : PLAIN_TEXT_EXTENSIONS) {
            if (StrUtil.equalsIgnoreCase(ext, plainExt)) {
                return true;
            }
        }
        return false;
    }
}
