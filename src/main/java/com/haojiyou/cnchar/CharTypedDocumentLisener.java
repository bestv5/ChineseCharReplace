package com.haojiyou.cnchar;

import com.haojiyou.cnchar.action.CharAutoReplaceAction;
import com.haojiyou.cnchar.common.CnCharCommentUtil;
import com.haojiyou.cnchar.common.MyConst;
import com.haojiyou.cnchar.common.ReplaceCharConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 实现DocumentListener接口，进行字符替换处理
 *
 * @author : best.xu
 */
public class CharTypedDocumentLisener implements DocumentListener {
    private static final Logger LOG = Logger.getInstance(CharTypedDocumentLisener.class);

    private Editor myEditor;
    private PsiFile myFile;


    public CharTypedDocumentLisener(Editor editor, PsiFile file) {
        this.myEditor = editor;
        this.myFile = file;
    }


    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
        DocumentListener.super.beforeDocumentChange(event);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        this.documentChanged(event, this.myEditor);
    }

    public void documentChanged(@NotNull DocumentEvent event, Editor editor) {
        if (myEditor == null) {
            if(LOG.isDebugEnabled()){
                LOG.info("editor is null");
            }
            return;
        }
        if (event.getNewLength() > 5 || !(myEditor instanceof EditorImpl)) {
            //输入长度大于5，不处理
            if(LOG.isDebugEnabled()){
                LOG.debug("length of the char > 5, do not replace");
            }

            return;
        }

        String originalText = event.getNewFragment().toString();
        String replacement = ReplaceCharConfig.cnCharMap.get(originalText);
        if (isCanBeReplaced(originalText, replacement, editor.getProject(), editor.getDocument(), editor, this.myFile)) {
            CharAutoReplaceAction.INSTANCE.replace(event, editor, originalText, replacement);
        }
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


    /**
     * 判断是否要替换
     *
     * @param originalText 输入字符
     * @param project      项目
     * @param document     文档对象
     * @param editor       当前的编辑器对象
     * @param file         当前文件对象
     * @return xx
     */
    private boolean isCanBeReplaced(String originalText, String replacement, Project project, Document document, Editor editor, PsiFile file) {
        if (StringUtils.isBlank(originalText)) {
            LOG.info("originalText is blank! return ");
            return false;
        }

        if (StringUtils.isBlank(replacement)) {
            //没有找到映射的值就不转换了
            LOG.info("not fond replace char in settings maps! it will not repleaced. ");
            return false;
        }

        final CaretModel caretModel = editor.getCaretModel();
        final Caret primaryCaret = caretModel.getPrimaryCaret();
        int caretOffset = primaryCaret.getOffset();
        //判断当前行是否是注释
        PsiComment comment;
        //当前光标元素
        PsiElement element = file.findElementAt(caretOffset);

        if (element == null) {
            //看自定义注释
            LOG.info("current offset element is null!");

            //txt file don't replace, at the same time, it can be compatible with the git commit input window.
            //if (StringUtils.equalsIgnoreCase("Dummy.txt", file.getName())) {
            if (StringUtils.equalsIgnoreCase(file.getFileType().getDefaultExtension(), MyConst.TXT)) {
                LOG.info("txt file doesn't replace!");
                return false;
            }

            if (CnCharCommentUtil.isCustomComment(document, project, caretOffset)) {
                //是自定义注释区域，不会替换中文字符。
                LOG.info("是自定义注释,是否要替换?:" + ReplaceCharConfig.replaceInComment);
                return ReplaceCharConfig.replaceInComment;
            }
            return true;
        }
        comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
        if (comment != null ) {
            return ReplaceCharConfig.replaceInComment;
        }

        if (element instanceof PsiWhiteSpace) {
            if (caretOffset > 0) {
                //如果光标位置是空，查找前一个光标的元素
                element = file.findElementAt(caretOffset - 1);
                comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
                if (comment != null) {
                    //如果开启了注释区域也要替换，就不用判断是否注释尾了
                    if (ReplaceCharConfig.replaceInComment){
                        return true;
                    }
                    //前一个光标位置是注释元素，要判断是否是块注释末尾
                    //如果是块注释末尾，说明当前输入是注释区域外（代码区），就要替换。
                    return CnCharCommentUtil.isAfterEndOfComment(document, project, caretOffset);
                }
            }
        }
        if (element != null) {
            comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
        }


        if (comment != null) {
            //comment 不为空，就认为此处是注释区域，不会替换中文字符。
            LOG.info("Jetbrains api 识别的注释不替换。");
            return ReplaceCharConfig.replaceInComment;
        }

        if (CnCharCommentUtil.isCustomComment(document, project, caretOffset)) {
            //是自定义注释区域，不会替换中文字符。
            LOG.info("是自定义注释,不替换。");
            return false;
        }

        if (StringUtils.equalsIgnoreCase(file.getFileType().getDefaultExtension(), MyConst.TXT)) {
            LOG.info("txt file doesn't replace!");
            return false;
        }
        return true;
    }
}
