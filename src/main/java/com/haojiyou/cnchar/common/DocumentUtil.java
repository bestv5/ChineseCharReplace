package com.haojiyou.cnchar.common;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;

/**
 * 描述:
 *
 * @author : lixuran
 */
public class DocumentUtil {

    /**
     * 获取当前光标行文本
     * @param document
     * @param currentOffset 当前光标位置
     * @return
     */
    public static String getLineText(Document document, int currentOffset){
        //当前行号
        int currentLineNumber = document.getLineNumber(currentOffset);
        //当前行开始、结束光标
        int currentLineStartOffset = document.getLineStartOffset(currentLineNumber);
        int currentLineEndOffset = document.getLineEndOffset(currentLineNumber);
        return document.getText(TextRange.create(currentLineStartOffset, currentLineEndOffset));
    }
}
