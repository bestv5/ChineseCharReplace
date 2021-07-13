package com.haojiyou.cnChar.service;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;

/**
 * 描述:
 *
 * @author : lixuran
 */
@Deprecated
public class FileTypeHandlerSevice {
    private TypedActionHandler orignTypedActionHandler;

    public void handle(Project project, Document document, char cn,char en, Caret primaryCaret, int caretOffset, String currentLineText,
                       DataContext dataContext){
        //String extension = FileEditorManager.getInstance(project).getSelectedEditor().getFile().getExtension();
        //PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        //PsiElement element = psiFile.findElementAt(caretOffset);
        //PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
        //
        //if (comment != null) {
        //    //comment 不为空，就认为此处是注释区域，不会替换中文字符。
        //    this.orignTypedActionHandler.execute(primaryCaret.getEditor(),cn,dataContext);
        //}else if (CommentUtil.isComment(currentLineText, FileType.getFileType(extension))){
        //    // 是自定义的注释区域
        //    this.orignTypedActionHandler.execute(primaryCaret.getEditor(),cn,dataContext);
        //}else {
        //    //否则，就认为是代码区域，都会替换中文字符。
        //    this.orignTypedActionHandler.execute(primaryCaret.getEditor(),en,dataContext);
        //}
    }

    public void setOrignTypedActionHandler(TypedActionHandler orignTypedActionHandler) {
        this.orignTypedActionHandler = orignTypedActionHandler;
    }
}
