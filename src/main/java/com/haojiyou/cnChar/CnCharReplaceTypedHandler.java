package com.haojiyou.cnChar;

import com.haojiyou.cnChar.common.CommentUtil;
import com.haojiyou.cnChar.common.FileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义事件处理类
 * @author best.xu
 */
public class CnCharReplaceTypedHandler implements TypedActionHandler {
    //缓存替换中英文字符配置
    public static Map<String, String> cnCharMap = new HashMap<>();

    //原始的处理类
    private TypedActionHandler orignTypedActionHandler;

    static {
        reload();
    }

    public CnCharReplaceTypedHandler(TypedActionHandler orignTypedActionHandler) {
        this.orignTypedActionHandler = orignTypedActionHandler;
    }

    public static void reload() {
        cnCharMap.clear();
        String[] configString = PropertiesComponent.getInstance().getValue(CnCharSettingComponent.KEY, CnCharSettingComponent.DEFAULT_STRING).split("\n");
        for (int i = 0; i < configString.length / 2; i++) {
            cnCharMap.put(configString[2 * i].trim(), configString[2 * i + 1].trim());
        }
    }

    @Override
    public void execute(@NotNull Editor editor, char c, @NotNull DataContext dataContext) {
        final Document document = editor.getDocument();
        final Project project = editor.getProject();

        String enChar = cnCharMap.get(String.valueOf(c));

        if (enChar == null ){
            //没有找到映射的值就不转换了
            this.orignTypedActionHandler.execute(editor, c, dataContext);
            return;
        }

        final CaretModel caretModel = editor.getCaretModel();
        final Caret primaryCaret = caretModel.getPrimaryCaret();
        int caretOffset = primaryCaret.getOffset();
        //当前行号
        int currentLineNumber = document.getLineNumber(caretOffset);

        //当前行开始、结束光标
        int currentLineStartOffset = document.getLineStartOffset(currentLineNumber);
        int currentLineEndOffset = document.getLineEndOffset(currentLineNumber);

        //当前行文本
        String currentLineText = document.getText(TextRange.create(currentLineStartOffset, currentLineEndOffset));
        //文件扩展名
        String extension = FileEditorManager.getInstance(project).getSelectedEditor().getFile().getExtension();
        //psiFile.getLanguage()

        // 当前文件
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

        //当前光标元素
        PsiElement element = psiFile.findElementAt(caretOffset);

        //判断当前行是否是注释
        PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);

        if (comment != null) {
            //comment 不为空，就认为此处是注释区域，不会替换中文字符。
            this.orignTypedActionHandler.execute(editor,c,dataContext);
        }else if (CommentUtil.isComment(currentLineText, FileType.getFileType(extension))){
            // 是自定义的注释区域
            this.orignTypedActionHandler.execute(editor,c,dataContext);
        }else {
            //否则，就认为是代码区域，都会替换中文字符。
            this.orignTypedActionHandler.execute(editor,enChar.charAt(0),dataContext);
        }

    }
}