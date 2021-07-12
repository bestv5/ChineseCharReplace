package com.haojiyou.cnChar;

import com.haojiyou.cnChar.service.FileTypeHandlerFactory;
import com.haojiyou.cnChar.service.FileTypeHandlerSevice;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述:
 *
 * @author : lixuran
 */
public class CnCharReplaceTypedHandler implements TypedActionHandler {
    public static Map<String, String> cnCharMap = new HashMap<>();
    private TypedActionHandler orignTypedActionHandler;
    private char
            lastChar = ' ';

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
        int currentLineNumber = document.getLineNumber(caretOffset);
        int currentLineStartOffset = document.getLineStartOffset(currentLineNumber);
        int currentLineEndOffset = document.getLineEndOffset(currentLineNumber);

        String currentLineText = document.getText(TextRange.create(currentLineStartOffset, currentLineEndOffset));

        FileTypeHandlerSevice fileTypeHandlerSevice = FileTypeHandlerFactory.getFileTypeHandlerSevice();
        if (fileTypeHandlerSevice != null){
            fileTypeHandlerSevice.setOrignTypedActionHandler(this.orignTypedActionHandler);
            fileTypeHandlerSevice.handle(project, document,c, enChar.charAt(0), primaryCaret,caretOffset, currentLineText,dataContext);
        }else {
            this.orignTypedActionHandler.execute(editor, c, dataContext);
        }

        //if (lastChar == '/' && enChar != null) {
        //
        //} else if (enChar != null) {
        //    this.orignTypedActionHandler.execute(editor, enChar.charAt(0), dataContext);
        //} else {
        //    this.orignTypedActionHandler.execute(editor, c, dataContext);
        //}

        this.lastChar = c;
    }
}