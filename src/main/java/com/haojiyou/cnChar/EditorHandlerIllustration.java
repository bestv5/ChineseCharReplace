package com.haojiyou.cnChar;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import org.jetbrains.annotations.NotNull;

/**
 * 中文符号替换成英文符号
 *
 * @author Mr.K
 */
public class EditorHandlerIllustration extends AnAction {
    static {
        final EditorActionManager actionManager = EditorActionManager.getInstance();
        final TypedAction typedAction = actionManager.getTypedAction();
        typedAction.setupHandler(new CnCharReplaceTypedHandler(typedAction.getHandler()));
    }


    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    }
}



