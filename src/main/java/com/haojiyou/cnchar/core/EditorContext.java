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

    /** 光标前文本（<b>不含刚键入字符</b>；回溯至行首或上限 16 字符），供 CjkContextDetector 语境判定使用。 */
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
     * 从光标位置向前提取语境窗口（纯函数，包可见以便单测直接覆盖）。
     *
     * <p><b>窗口终点为 {@code offset - 1}，即排除刚键入的字符</b>：{@code charTyped} 触发时该字符
     * 已插入文档且恰位于 {@code offset - 1} 处，而本窗口供 {@link CjkContextDetector} 判定
     * <b>键入前</b>的语境——若把键入字符自身包含进来，键入的中文标点会把上下文"自污染"成中文，
     * 导致 ADAPTIVE 区域替换被静默跳过（P0 缺陷）。例如前文 "hello" 时键入 '。'，生产窗口必须是
     * "hello"（英文语境 → REPLACE），而非 "hello。"（被误判中文 → SKIP）。
     *
     * <p>边界约定：
     * <ul>
     *   <li>{@code offset <= 0} 或空文档 → 空窗口；</li>
     *   <li>键入的是文档首字符（{@code offset == 1}）→ 空窗口（其前无任何上下文，检测器保守 SKIP）；</li>
     *   <li>行首边界以 {@code offset - 1} 所在行为准：
     *       键入字符为行中普通字符 → 窗口为同一行内其前至多 16 字符；
     *       键入字符恰为某行第一个字符 → 回溯步数归零，窗口为空（检测器保守 SKIP），不跨行取更早的行；
     *       键入字符为换行符 → {@code offset - 1} 即换行符下标（平台语义上行分隔符归前行），
     *       窗口为上一行行尾内容（≤16 字符），与"回溯至行首"的既有回溯逻辑一致。</li>
     * </ul>
     */
    static String extractTextBefore(Document document, int offset) {
        if (offset <= 0 || document.getTextLength() == 0) {
            return "";
        }
        // 窗口终点（开区间）= 刚键入字符所在下标；键入的是文档首字符时其前无上下文
        int windowEnd = offset - 1;
        if (windowEnd <= 0) {
            return "";
        }
        int lineStart = document.getLineStartOffset(document.getLineNumber(windowEnd));
        if (lineStart > windowEnd) {
            // 防御：键入的是换行符且平台把行分隔符归入下一行行界时，lineStart 会越过窗口终点，
            // 此时视作行内无前文（空窗口），避免负向回溯导致 substring 越界
            return "";
        }
        int lookback = Math.min(windowEnd - lineStart, 16);
        int from = windowEnd - lookback;
        if (from < 0) {
            from = 0;
        }
        return document.getText().substring(from, windowEnd);
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
     * 光标前文本（不含刚键入字符；回溯至行首或上限 16 字符）。
     */
    @NotNull
    public String getTextBeforeCursor() {
        return textBeforeCursor;
    }
}
