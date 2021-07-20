package com.haojiyou.cnchar.handler;

import com.haojiyou.cnchar.CharTypedDocumentLisener;
import com.haojiyou.cnchar.common.CommentUtil;
import com.haojiyou.cnchar.common.ReplaceCharConfig;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
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
        LOG.info("your inputed char :" + c);
        String enChar = ReplaceCharConfig.cnCharMap.get(String.valueOf(c));
        if (enChar == null) {
            //没有找到映射的值就不转换了
            LOG.info("not fond replace char in settings maps! it will not repleaced.");
            return Result.CONTINUE;
        }


        Document document = editor.getDocument();


        final CaretModel caretModel = editor.getCaretModel();

        final Caret primaryCaret = caretModel.getPrimaryCaret();
        int caretOffset = primaryCaret.getOffset();


        //判断当前行是否是注释
        PsiComment comment = null;
        //当前光标元素
        PsiElement element = file.findElementAt(caretOffset);
        if (element == null) {
            LOG.info("current offset element is null!");
        } else {
            comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
        }

        if (comment != null) {
            //comment 不为空，就认为此处是注释区域，不会替换中文字符。
            LOG.info("Jetbrains api 识别的注释不替换。");
        } else if (CommentUtil.isCustomComment(document, project, caretOffset)) {
            //是自定义注释区域，不会替换中文字符。
            LOG.info("是自定义注释,不替换。");
        } else {
            //不是自定义注释区域，准备替换字符工作
            LOG.info("add DocumentListener...CharTypedDocumentLisener");
            document.addDocumentListener(new CharTypedDocumentLisener(editor));
        }
        return Result.CONTINUE;
    }
}
