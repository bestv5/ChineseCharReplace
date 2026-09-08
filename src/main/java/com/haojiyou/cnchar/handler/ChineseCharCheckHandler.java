package com.haojiyou.cnchar.handler;

import com.haojiyou.cnchar.convert.CharConverter;
import com.haojiyou.cnchar.core.EditorContext;
import com.haojiyou.cnchar.core.PolicyEngine;
import com.haojiyou.cnchar.core.RegionClassifier;
import com.haojiyou.cnchar.core.ReplacementExecutor;
import com.haojiyou.cnchar.region.InputRegion;
import com.haojiyou.cnchar.rule.ReplacementRuleChain;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 中文字符检测处理 —— charTyped 管线入口。
 *
 * <p>管线流程：O(1) 候选门控 → EditorContext → 前置规则链 → 区域分类 → 策略决策 → 转换 → 替换执行。
 * 彻底移除每键 add/removeDocumentListener，替换在 WriteCommandAction 内同步完成（单步撤销）。
 *
 * @author : best.xu
 */
public class ChineseCharCheckHandler extends TypedHandlerDelegate {
    private static final Logger LOG = Logger.getInstance(ChineseCharCheckHandler.class);

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        CharAutoReplaceSettings settings = CharAutoReplaceSettings.getInstance();
        if (!settings.isCandidateChar(c)) {
            return Result.CONTINUE;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("candidate char typed: " + c);
        }

        CharAutoReplaceSettings.Snapshot snapshot = settings.getSnapshot();
        int offset = editor.getCaretModel().getOffset();
        EditorContext ctx = new EditorContext(project, editor, editor.getDocument(), file, offset, c);

        ReplacementRuleChain chain = ReplacementRuleChain.defaultChain(snapshot);
        if (!chain.checkAll(ctx)) {
            return Result.CONTINUE;
        }

        InputRegion region = RegionClassifier.classify(ctx);
        if (region == InputRegion.UNREACHABLE) {
            return Result.CONTINUE;
        }

        PolicyEngine.Decision decision = PolicyEngine.decide(region, ctx, snapshot);
        if (decision == PolicyEngine.Decision.SKIP) {
            return Result.CONTINUE;
        }

        CharConverter converter = settings.createConverter();
        ReplacementExecutor.execute(ctx, converter, snapshot);

        return Result.CONTINUE;
    }
}
