package com.haojiyou.cnchar.core;

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 描述: 注释/字符串上下文解析器 —— PSI 主判 + 平台 Commenter 兜底。
 *
 * <p>判定优先级：
 * <ol>
 *   <li>PSI：{@link PsiComment} → 注释区；{@link PsiLanguageInjectionHost} → 字符串/注入区</li>
 *   <li>Commenter 兜底：当 PSI 无法判定（语言插件不支持 / 纯文本）时，
 *       用 {@link LanguageCommenters#forLanguage} 获取行级注释标记，检查当前行是否以注释标记开头</li>
 *   <li>都不可用 → 视为非注释/非字符串（由外层 RegionClassifier 决定最终归类）</li>
 * </ol>
 *
 * <p><b>语言无关</b>：仅使用 {@code com.intellij.psi.*} 通用接口，不引用任何语言专属 PSI 类，
 * 保持 CLion/Rider 兼容。
 *
 * @author : best.xu
 */
public final class CommentContextResolver {

    private CommentContextResolver() {
    }

    /**
     * 上下文判定结果。
     */
    public enum ContextType {
        COMMENT,
        STRING,
        CODE,
        UNKNOWN
    }

    /**
     * 判定光标所在位置的上下文类型。
     *
     * @param psiFile 当前 PSI 文件（可为 null，此时直接返回 UNKNOWN）
     * @param offset  光标偏移
     * @param document 当前文档（Commenter 兜底时需要）
     * @return 上下文类型
     */
    @NotNull
    public static ContextType resolve(@Nullable PsiFile psiFile,
                                      int offset,
                                      @Nullable Document document,
                                      @Nullable Project project) {
        if (psiFile == null) {
            return ContextType.UNKNOWN;
        }
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null && offset > 0) {
            element = psiFile.findElementAt(offset - 1);
        }
        if (element == null) {
            return ContextType.UNKNOWN;
        }

        PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
        if (comment != null) {
            return ContextType.COMMENT;
        }

        PsiLanguageInjectionHost injectionHost =
                PsiTreeUtil.getParentOfType(element, PsiLanguageInjectionHost.class, false);
        if (injectionHost != null && !(injectionHost instanceof PsiComment)) {
            return ContextType.STRING;
        }

        if (document != null) {
            if (isCommentByCommenter(psiFile, document, offset)) {
                return ContextType.COMMENT;
            }
        }

        return ContextType.CODE;
    }

    /**
     * 使用平台 Commenter 兜底判定：当前行是否以注释标记开头。
     */
    private static boolean isCommentByCommenter(@NotNull PsiFile psiFile,
                                                @NotNull Document document,
                                                int offset) {
        Language language = psiFile.getLanguage();
        Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
        if (commenter == null) {
            return false;
        }
        int lineNumber = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        String lineText = document.getText().substring(lineStart, lineEnd).trim();

        String linePrefix = commenter.getLineCommentPrefix();
        if (linePrefix != null && !linePrefix.isEmpty() && lineText.startsWith(linePrefix)) {
            return true;
        }

        String blockPrefix = commenter.getBlockCommentPrefix();
        String blockSuffix = commenter.getBlockCommentSuffix();
        if (blockPrefix != null && blockSuffix != null) {
            if (lineText.startsWith(blockPrefix) || lineText.endsWith(blockSuffix)) {
                return true;
            }
        }

        return false;
    }
}
