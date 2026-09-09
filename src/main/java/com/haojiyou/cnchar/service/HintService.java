package com.haojiyou.cnchar.service;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;

import javax.swing.event.HyperlinkListener;

/**
 * 描述:
 *
 * @author : lixuran
 */
public class HintService {

    private static String TIPS = "<html><div>\n<div>\n  <span style=\"color: #777777; font-size: 1em;\">AutoFix:</span>&nbsp;&nbsp;" +
            "CONTENT\n</div>\n<label  style=\"color: #2470b3; font-size: 0.9em; \">TIPS</label>\n</div></html>";

public static HintService getInstance(){
    return ApplicationManager.getApplication().getService(HintService.class);
}


    /**
     * 在编辑器光标处展示信息提示（公共 API 版）。
     *
     * <p>已从内部类 {@code HintManagerImpl} 迁移到公共 {@link HintManager}：
     * 原手工流程（HintUtil.createInformationLabel → AccessibleContextUtil.setName("Hint") →
     * new LightweightHint → getHintPosition(ABOVE) → showEditorHint）与平台公共 API
     * {@link HintManager#showInformationHint(Editor, String, HyperlinkListener)} 的内部实现
     * <b>基本等价</b>（含 isUnitTestMode 早退，判定逻辑无分叉），但非逐项等价，差异见下。
     *
     * <p><b>已知残留差异</b>：
     * <ul>
     *   <li><b>可访问性名称缺失</b>：原手工流程含 {@code AccessibleContextUtil.setName(label, "Hint")}
     *       （辅助功能/读屏名称），公共 {@code showInformationHint} 内部构建的 label 不含此步骤。
     *       javap 203 核实：存在理论恢复路径 {@code HintUtil.createInformationLabel → setName →
     *       HintManager#showInformationHint(Editor, JComponent)}（公共重载，platform-api 实证），
     *       但该重载不接受 {@link HyperlinkListener}（本方法现有调用携带 listener），且会引入
     *       新的 label 构建与展示路径差异；待 runIde 确认现有路径行为后再评估，暂不实施；</li>
     *   <li>原手工调用使用的精确偏移 (12,0) 与 hide flags=12 已被平台默认值取代；提示的
     *       定位/隐藏细微行为需 runIde 实测观察，如出现可感知偏差，再评估处理方案
     *       （勿直接改回内部 API）。</li>
     * </ul>
     */
    public void showHint(Editor editor, String text, HyperlinkListener hyperlinkListener) {
        HintManager.getInstance().showInformationHint(editor, text, hyperlinkListener);
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
}
