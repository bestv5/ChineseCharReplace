package com.haojiyou.cnchar.handler;

import com.haojiyou.cnchar.MyDocumentLisener;
import com.haojiyou.cnchar.common.CommentUtil;
import com.haojiyou.cnchar.common.ReplaceCharConfig;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 中文字符检测处理
 *
 * @author : best.xu
 */
public class ChineseCharCheckHandler extends TypedHandlerDelegate {

    @Override
    public @NotNull Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
        String enChar = ReplaceCharConfig.cnCharMap.get(String.valueOf(c));
        if (enChar == null) {
            //没有找到映射的值就不转换了
            return super.beforeCharTyped(c, project, editor, file, fileType);
        }


        Document document = editor.getDocument();
        final CaretModel caretModel = editor.getCaretModel();

        final Caret primaryCaret = caretModel.getPrimaryCaret();
        int caretOffset = primaryCaret.getOffset();

        //当前光标元素
        PsiElement element = file.findElementAt(caretOffset);
        if (element == null){
            return super.beforeCharTyped(c, project, editor, file, fileType);
        }
        //判断当前行是否是注释
        PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
        if (comment != null) {
            //comment 不为空，就认为此处是注释区域，不会替换中文字符。
            return super.beforeCharTyped(c, project, editor, file, fileType);
        } else {
            //当前行号
            int currentLineNumber = document.getLineNumber(caretOffset);

            //当前行开始、结束光标
            int currentLineStartOffset = document.getLineStartOffset(currentLineNumber);
            int currentLineEndOffset = document.getLineEndOffset(currentLineNumber);

            //当前行文本
            String currentLineText = document.getText(TextRange.create(currentLineStartOffset, currentLineEndOffset));
            //文件扩展名
            String extension = FileEditorManager.getInstance(project).getSelectedEditor().getFile().getExtension();

            if (!CommentUtil.isComment(currentLineText, com.haojiyou.cnchar.common.FileType.getFileType(extension))) {
                //不是自定义注释区域，准备替换字符工作
                document.addDocumentListener(new MyDocumentLisener());
            }

            return super.beforeCharTyped(c, project, editor, file, fileType);

        }


    }
}
