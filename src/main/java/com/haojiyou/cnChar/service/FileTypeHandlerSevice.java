package com.haojiyou.cnChar.service;

import com.haojiyou.cnChar.common.CommentUtil;
import com.haojiyou.cnChar.common.FileType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * 描述:
 *
 * @author : lixuran
 */
public class FileTypeHandlerSevice {
    private TypedActionHandler orignTypedActionHandler;

    public void handle(Project project, Document document, char cn,char en, Caret primaryCaret, int caretOffset, String currentLineText,
                       DataContext dataContext){
        String extension = FileEditorManager.getInstance(project).getSelectedEditor().getFile().getExtension();
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        PsiElement element = psiFile.findElementAt(caretOffset);
        if (element instanceof PsiComment && element.getTextOffset() < caretOffset) {
            this.orignTypedActionHandler.execute(primaryCaret.getEditor(),cn,dataContext);
        }else if (CommentUtil.isComment(currentLineText, FileType.getFileType(extension))){
            this.orignTypedActionHandler.execute(primaryCaret.getEditor(),cn,dataContext);
        }else {
            //Runnable runnable = () -> {
            //    document.replaceString(caretOffset - 1, caretOffset, String.valueOf(en));
            //    //document.deleteString(caretOffset - 1, caretOffset);
            //    //document.insertString(caretOffset - 1, String.valueOf(en));
            //    //primaryCaret.moveToOffset(caretOffset);
            //};
            //// Must do this document change in a write action context.
            //WriteCommandAction.runWriteCommandAction(project, runnable);

            // De-select the text range that was just replaced
            //primaryCaret.moveToOffset(caretOffset);
            this.orignTypedActionHandler.execute(primaryCaret.getEditor(),en,dataContext);

        }
    }

    public void setOrignTypedActionHandler(TypedActionHandler orignTypedActionHandler) {
        this.orignTypedActionHandler = orignTypedActionHandler;
    }
}
