package com.haojiyou.cnchar.core;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
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
 *   <li>COMMENT / STRING / CODE：由 {@link CommentContextResolver} PSI 判定</li>
 * </ol>
 *
 * <p><b>弃用 {@code getSelectedEditor()}</b>：分屏场景会误判，所有判定均从事件传入的 Editor/PsiFile 派生。
 *
 * @author : best.xu
 */
public final class RegionClassifier {

    private static final String COMMIT_MESSAGE_USER_DATA_KEY = "Git.CommitMessage";

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
                CommentContextResolver.resolve(psiFile, ctx.getOffset(), ctx.getDocument(), project);

        switch (contextType) {
            case COMMENT:
                return InputRegion.COMMENT;
            case STRING:
                return InputRegion.STRING;
            case CODE:
                return InputRegion.CODE;
            case UNKNOWN:
            default:
                if (psiFile == null) {
                    return InputRegion.UNREACHABLE;
                }
                return InputRegion.CODE;
        }
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
        Object commitMarker = editor.getUserData(getCommitUserDataKey());
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

    private static com.intellij.openapi.util.Key<Object> getCommitUserDataKey() {
        return com.intellij.openapi.util.Key.create(COMMIT_MESSAGE_USER_DATA_KEY);
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
        String ext = fileType.getDefaultExtension();
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
