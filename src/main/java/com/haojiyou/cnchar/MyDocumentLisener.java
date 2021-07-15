package com.haojiyou.cnchar;

import com.haojiyou.cnchar.common.ReplaceCharConfig;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 实现DocumentListener接口，进行字符替换处理
 *
 * @author : best.xu
 */
public class MyDocumentLisener implements DocumentListener {

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
        DocumentListener.super.beforeDocumentChange(event);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {

        String text = event.getNewFragment().toString();
        String enChar = ReplaceCharConfig.cnCharMap.get(text);

        if (enChar == null) {
            //没有找到映射的值就不转换了
            return;
        }

        Document document = event.getDocument();
        Project project = CommandProcessor.getInstance().getCurrentCommandProject();

        int caretOffset = event.getOffset();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        document.replaceString(caretOffset, caretOffset + 1, enChar);

                    }
                });

            }
        });
        document.removeDocumentListener(MyDocumentLisener.this);

    }


    @Override
    public void bulkUpdateStarting(@NotNull Document document) {
        DocumentListener.super.bulkUpdateStarting(document);
    }

    @Override
    public void bulkUpdateFinished(@NotNull Document document) {
        DocumentListener.super.bulkUpdateFinished(document);
    }
}
