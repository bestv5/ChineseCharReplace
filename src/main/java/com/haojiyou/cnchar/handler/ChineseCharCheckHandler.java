package com.haojiyou.cnchar.handler;

import com.haojiyou.cnchar.CharTypedDocumentLisener;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 中文字符检测处理
 *
 * @author : best.xu
 */
public class ChineseCharCheckHandler extends TypedHandlerDelegate {
    private static final Logger LOG = Logger.getInstance(ChineseCharCheckHandler.class);

    @Override
    public @NotNull Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
        LOG.info("handler input char: "+ c);
        if (StringUtils.isNotBlank(String.valueOf(c))) {
            LOG.info("add DocumentListener...CharTypedDocumentLisener");
            editor.getDocument().addDocumentListener(new CharTypedDocumentLisener(editor, file));
        }
        return Result.CONTINUE;
    }


}
