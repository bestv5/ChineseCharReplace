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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 中文字符检测处理 —— charTyped 管线入口。
 *
 * <p>管线流程：O(1) 候选门控 → PSI 提交（commitDocument，失败落回文本兜底）→ EditorContext → 前置规则链 → 区域分类 → 策略决策 → 转换 → 替换执行。
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
        Document document = editor.getDocument();

        // 设计依据：刚键入的字符此刻仅落在 Document，尚未进入 PSI 快照；区域分类
        // （CommentContextResolver 的注释/字符串/代码 PSI 主判）依赖最新 PSI，
        // 故在构造 EditorContext 前先提交文档。
        // commitDocument 对已提交文档幂等零开销，且在 EDT typing 上下文中安全；
        // 提交失败（如意外在后台线程触发）时仅告警并继续，自然落回既有文本兜底路径
        // （textBeforeCursor + CjkContextDetector），不影响主流程。
        try {
            PsiDocumentManager.getInstance(project).commitDocument(document);
        } catch (ProcessCanceledException e) {
            // 取消信号必须原样抛出，不可吞并
            throw e;
        } catch (Exception e) {
            // 异常路径开销可忽略，warn 直接输出（不按 isDebugEnabled 门控，避免生产环境 commitDocument 失败完全静默）
            LOG.warn("PSI commitDocument failed, fallback to text-based heuristic: char=" + c + ", offset=" + offset, e);
        }

        EditorContext ctx = new EditorContext(project, editor, document, file, offset, c);

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
