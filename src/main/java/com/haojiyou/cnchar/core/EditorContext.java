package com.haojiyou.cnchar.core;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 描述: 每次击键的只读上下文快照（纯数据载体，不可变）。
 *
 * <p>在 {@code charTyped} 入口构建一次，随后在 RegionClassifier → PolicyEngine → CharConverter →
 * ReplacementExecutor 管线中传递，避免各层重复从 Editor/Document 取数。
 *
 * <p><b>线程约定</b>：所有字段均在 EDT 上读取（typedHandler 链路保证），无需额外同步。
 *
 * @author : best.xu
 */
public class EditorContext {

    private final Project project;
    private final Editor editor;
    private final Document document;
    private final PsiFile psiFile;
    private final int offset;
    private final char typedChar;

    /** 光标前文本（向前取至行首或上限 N 字符），供 CjkContextDetector / 多字符尾部匹配使用。 */
    private final String textBeforeCursor;

    public EditorContext(@NotNull Project project,
                         @NotNull Editor editor,
                         @NotNull Document document,
                         @Nullable PsiFile psiFile,
                         int offset,
                         char typedChar) {
        this.project = project;
        this.editor = editor;
        this.document = document;
        this.psiFile = psiFile;
        this.offset = offset;
        this.typedChar = typedChar;
        this.textBeforeCursor = extractTextBefore(document, offset);
    }

    /**
     * 受保护构造：供测试使用，直接指定 textBeforeCursor，绕过 Document 提取。
     */
    protected EditorContext(char typedChar, @NotNull String textBeforeCursor) {
        this.project = null;
        this.editor = null;
        this.document = null;
        this.psiFile = null;
        this.offset = 0;
        this.typedChar = typedChar;
        this.textBeforeCursor = textBeforeCursor;
    }

    /**
     * 从光标位置向前取文本，至行首或上限 16 字符（供 CJK 语境启发式使用）。
     */
    private static String extractTextBefore(Document document, int offset) {
        if (offset <= 0 || document.getTextLength() == 0) {
            return "";
        }
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        int lookback = Math.min(offset - lineStart, 16);
        int from = offset - lookback;
        if (from < 0) {
            from = 0;
        }
        return document.getText().substring(from, offset);
    }

    @NotNull
    public Project getProject() {
        return project;
    }

    @NotNull
    public Editor getEditor() {
        return editor;
    }

    @NotNull
    public Document getDocument() {
        return document;
    }

    @Nullable
    public PsiFile getPsiFile() {
        return psiFile;
    }

    public int getOffset() {
        return offset;
    }

    public char getTypedChar() {
        return typedChar;
    }

    /**
     * 光标前文本（至行首或上限 16 字符，跳过空白后回溯）。
     */
    @NotNull
    public String getTextBeforeCursor() {
        return textBeforeCursor;
    }
}
