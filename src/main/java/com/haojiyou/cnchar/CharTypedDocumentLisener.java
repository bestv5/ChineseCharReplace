package com.haojiyou.cnchar;

import com.haojiyou.cnchar.action.CharAutoReplaceAction;
import com.haojiyou.cnchar.common.ReplaceCharConfig;
import com.haojiyou.cnchar.handler.ChineseCharCheckHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 实现DocumentListener接口，进行字符替换处理
 *
 * @author : best.xu
 */
public class CharTypedDocumentLisener implements DocumentListener {
    private static final Logger LOG = Logger.getInstance(CharTypedDocumentLisener.class);

    private Editor editor;


    public CharTypedDocumentLisener(Editor editor){
        this.editor = editor;
    }


    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
        DocumentListener.super.beforeDocumentChange(event);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        this.documentChanged(event, this.editor);
    }

    public void documentChanged(@NotNull DocumentEvent event, Editor editor) {
        if (editor == null){
            return;
        }
        if (event.getNewLength() > 5 || !(editor instanceof EditorImpl)){
            //输入长度大于5，不处理
            return;
        }

        String originalText = event.getNewFragment().toString();
        if (StringUtils.isBlank(originalText)){
            return;
        }

        String replacement = ReplaceCharConfig.cnCharMap.get(originalText);
        if (StringUtils.isBlank(replacement)) {
            //没有找到映射的值就不转换了
            return;
        }

        CharAutoReplaceAction.INSTANCE.replace(event,editor,originalText,replacement);

        event.getDocument().removeDocumentListener(CharTypedDocumentLisener.this);

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
