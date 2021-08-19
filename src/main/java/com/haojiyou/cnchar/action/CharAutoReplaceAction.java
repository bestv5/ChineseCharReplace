package com.haojiyou.cnchar.action;

import com.haojiyou.cnchar.common.ReplaceCharConfig;
import com.haojiyou.cnchar.service.HintService;
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
        if (project == null){
            LOG.warn("projcet is null! returned.");
            return;
        }

        int currentOffset = event.getOffset() + event.getNewLength();

        ApplicationManager.getApplication().invokeLater(()-> {
            if (editor.isDisposed()) {
                LOG.info("editor is disposed, project:"+editor.getProject().getName());
                return;
            }
            WriteCommandAction.runWriteCommandAction(project, () -> {
                document.replaceString(event.getOffset(), currentOffset, replacement);
                LOG.info("input char: "+ originalText +" replaced: " + replacement);
                LOG.info("replace msg hint config:" + ReplaceCharConfig.showRepacedMsg);
                if (ReplaceCharConfig.showRepacedMsg){
                    HintService.getInstance().showHint((EditorImpl) editor, HintService.INSTANCE.createHint(originalText,
                            replacement),null);
                }
            });
        });

    }
}
