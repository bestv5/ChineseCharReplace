package com.haojiyou.cnchar.core;

import com.haojiyou.cnchar.common.StrUtil;
import com.haojiyou.cnchar.convert.CharConverter;
import com.haojiyou.cnchar.service.HintService;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 替换执行器 —— 在 charTyped 写动作内同步替换，处理长度变化与光标、单步撤销、触发提示。
 *
 * <p><b>核心约定</b>：
 * <ul>
 *   <li>处于打字 {@link WriteCommandAction} 内，替换并入同一撤销步（修复撤销碎片化）</li>
 *   <li>一对多替换（如 {@code …→...}）后重算偏移并把光标置于替换串末尾</li>
 *   <li>多字符键兜底匹配：对光标前尾部做一次最长优先匹配</li>
 * </ul>
 *
 * @author : best.xu
 */
public final class ReplacementExecutor {

    private ReplacementExecutor() {
    }

    /**
     * 执行替换。
     *
     * @param ctx         击键上下文
     * @param converter   转换器（已绑定快照）
     * @param snapshot    当前配置快照
     */
    public static void execute(@NotNull EditorContext ctx,
                               @NotNull CharConverter converter,
                               @NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        Editor editor = ctx.getEditor();
        Document document = ctx.getDocument();
        Project project = ctx.getProject();
        int offset = ctx.getOffset();
        char typedChar = ctx.getTypedChar();

        CharConverter.Conversion conversion = converter.convertChar(typedChar);

        if (conversion == null && !snapshot.getMultiCharFirstChars().isEmpty()) {
            String beforeCursor = document.getText().substring(Math.max(0, offset - CharConverter.MAX_MULTI_CHAR_LEN), offset);
            conversion = converter.convertTail(beforeCursor);
            if (conversion != null) {
                int matchLen = beforeCursor.length();
                for (int size = Math.min(beforeCursor.length(), CharConverter.MAX_MULTI_CHAR_LEN); size >= 2; size--) {
                    String key = beforeCursor.substring(beforeCursor.length() - size);
                    if (converter.convertTail(key) != null) {
                        matchLen = size;
                        break;
                    }
                }
                int replaceStart = offset - matchLen;
                doReplace(editor, document, project, replaceStart, offset, conversion, typedChar, snapshot);
                return;
            }
        }

        if (conversion == null) {
            return;
        }

        int replaceStart = offset - 1;
        doReplace(editor, document, project, replaceStart, offset, conversion, typedChar, snapshot);
    }

    private static void doReplace(@NotNull Editor editor,
                                  @NotNull Document document,
                                  @NotNull Project project,
                                  int replaceStart,
                                  int replaceEnd,
                                  @NotNull CharConverter.Conversion conversion,
                                  char typedChar,
                                  @NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            if (editor.isDisposed()) {
                return;
            }
            document.replaceString(replaceStart, replaceEnd, conversion.text);
            CaretModel caretModel = editor.getCaretModel();
            int newCaretOffset = replaceStart + conversion.text.length();
            caretModel.moveToOffset(newCaretOffset);

            if (snapshot != null && shouldShowHint(snapshot)) {
                showHint(editor, String.valueOf(typedChar), conversion.text);
            }
        });
    }

    private static boolean shouldShowHint(@NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        return false;
    }

    private static void showHint(@NotNull Editor editor, @NotNull String original, @NotNull String replacement) {
        try {
            HintService hintService = HintService.getInstance();
            String hint = hintService.createHint(original, replacement);
            if (editor instanceof com.intellij.openapi.editor.impl.EditorImpl) {
                hintService.showHint((com.intellij.openapi.editor.impl.EditorImpl) editor, hint, null);
            }
        } catch (Exception e) {
            // 提示失败不影响主流程
        }
    }
}
