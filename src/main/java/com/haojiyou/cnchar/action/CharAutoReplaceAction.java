package com.haojiyou.cnchar.action;

import com.haojiyou.cnchar.common.DocumentUtil;
import com.haojiyou.cnchar.handler.HintHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 字符自动替换
 *
 * @author : best.xu
 */
public class CharAutoReplaceAction {
    private static final Logger LOG = Logger.getInstance(CharAutoReplaceAction.class);

    public static CharAutoReplaceAction INSTANCE = new CharAutoReplaceAction();

    public synchronized void replace(@NotNull DocumentEvent event, Editor editor,String originalText, String replacement){
        Document document = event.getDocument();
        Project project = editor.getProject();
        int currentOffset = event.getOffset() + event.getNewLength();

        ApplicationManager.getApplication().invokeLater(()-> {
            WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                @Override
                public void run() {
                    document.replaceString(event.getOffset(), currentOffset, replacement);
                    HintHandler.INSTANCE.showHint((EditorImpl) editor, currentOffset,HintHandler.INSTANCE.createHint(originalText,
                            replacement),null);
                }
            });
        });

    }

    public synchronized void  replace2(@NotNull DocumentEvent event, Editor editor,String originalText, String replacement){
        Document document = event.getDocument();
        Project project = editor.getProject();
        int currentOffset = event.getOffset() + event.getNewLength();
        Runnable replaceTask = new Runnable() {
            @Override
            public void run() {
                document.replaceString(event.getOffset(), currentOffset, replacement);
            }
        };

        ApplicationManager.getApplication().invokeAndWait(() -> {
            if (editor.isDisposed()) {
                return;
            }
            WriteCommandAction.runWriteCommandAction(project, replaceTask);
        });


    }
}
