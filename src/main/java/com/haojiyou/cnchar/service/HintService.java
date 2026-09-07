package com.haojiyou.cnchar.service;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

/**
 * 描述:
 *
 * @author : lixuran
 */
public class HintService {

    public static final HintService INSTANCE = new HintService();

    private static String TIPS = "<html><div>\n<div>\n  <span style=\"color: #777777; font-size: 1em;\">AutoFix:</span>&nbsp;&nbsp;" +
            "CONTENT\n</div>\n<label  style=\"color: #2470b3; font-size: 0.9em; \">TIPS</label>\n</div></html>";

public static HintService getInstance(){
    return ApplicationManager.getApplication().getService(HintService.class);
}


    public void showHint(EditorImpl editor, String text, HyperlinkListener hyperlinkListener) {
        HintManagerImpl hintManager = (HintManagerImpl) HintManagerImpl.getInstance();
        JComponent label = HintUtil.createInformationLabel(text, hyperlinkListener, null, null);
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
            AccessibleContextUtil.setName(label, "Hint");
            LightweightHint hint = new LightweightHint(label);
            Point p = HintManagerImpl.getHintPosition(hint, (Editor) editor, editor.getCaretModel().getVisualPosition(), (short) 1);
            hintManager.showEditorHint(hint, (Editor) editor, p, 12, 0, true, (short) 1);
        }

    }

    public void showHint(EditorImpl editor, int logicalPositionLine,int logicalPositionColumn, String text, HyperlinkListener hyperlinkListener) {

        HintManagerImpl hintManager = (HintManagerImpl) HintManagerImpl.getInstance();
        JComponent label = HintUtil.createInformationLabel(text, hyperlinkListener, null, null);
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
            AccessibleContextUtil.setName(label, "Hint");
            LightweightHint hint = new LightweightHint(label);
            LogicalPosition logicalPosition = new LogicalPosition(logicalPositionLine, logicalPositionColumn);
            VisualPosition visualPos = editor.logicalToVisualPosition(logicalPosition);
            Point p = HintManagerImpl.getHintPosition(hint, (Editor) editor, visualPos, (short) 1);
            hintManager.showEditorHint(hint, (Editor) editor, p, 12, 0, true, (short) 1);
        }

    }


    public String createHint(String original, String replacement) {
        original = originalHtml(escapeHtml(original));
        replacement = replacement(escapeHtml(replacement));
        String title = "提示: ";
        String content = original + "已被改为" + replacement;
        String apply = "如果不需要替换，请按[撤回]的快捷键还原.";
        return TIPS.replace("AutoFix:", title)
                .replace("CONTENT", "<span style=\"color: #777777; font-size: 1em;\">" + content + "</span>\n").replace("TIPS", apply);
    }

    /**
     * 使用纯 JDK 实现 HTML 转义，避免依赖 commons-text（平台运行时不自带该库）。
     * 注意 '&' 必须最先处理，避免对已生成的实体进行二次转义。
     *
     * @param s 原始字符串，允许为 null
     * @return 转义后的字符串；入参为 null 时返回空字符串
     */
    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private String originalHtml(String s) {
        return "<code style=\"color: #ef5149; font-size: 1.5em;\">" + s + "</code>";
    }

    private String replacement(String s) {
        return "<code style=\"color: #5c962c; font-size: 1.5em;\">" + s + "</code>";
    }


    private LogicalPosition getLogicalPosition(Document document,int offset) {
        int line = document.getLineNumber(offset);
        int column = offset -1;
        return new LogicalPosition(line, column);
    }
}
