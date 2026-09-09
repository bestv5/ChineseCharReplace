package com.haojiyou.cnchar.core;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 描述: 注释/字符串上下文解析器 —— PSI 主判 + SyntaxHighlighter token 兜底 + Commenter 兜底。
 *
 * <p>判定优先级：
 * <ol>
 *   <li>PSI：{@link PsiComment} → 注释区；{@link PsiLanguageInjectionHost} → 字符串/注入区</li>
 *   <li><b>token 兜底层（计划分组 C）</b>：PSI 主判未命中时，若能拿到编辑器高亮
 *       {@link EditorHighlighter}（live 优先 {@code EditorEx.getHighlighter()}，备选
 *       {@code HighlighterFactory.createHighlighter(Project, VirtualFile)}），取光标处 token 的
 *       {@code SyntaxHighlighter.getTokenHighlights(tokenType)} key 数组：命中
 *       {@link DefaultLanguageHighlighterColors#STRING} → STRING；命中 LINE_COMMENT/BLOCK_COMMENT/
 *       DOC_COMMENT → COMMENT。用于 PSI 无法判定注释/字符串语义的语言（如未实现注入接口的
 *       C++/SQL/CSS）。<b>仅兜底</b>：主判命中时不进入本层，XML 文本节点等主判行为不受影响。</li>
 *   <li>Commenter 兜底：token 层也不可用时，用 {@link LanguageCommenters#forLanguage} 获取
 *       行级注释标记，检查当前行是否以注释标记开头</li>
 *   <li>都不可用 → CODE（由外层 RegionClassifier 决定最终归类；能力缓存判为两者皆不可用时
 *       短路返回 UNKNOWN，见下方能力缓存说明）</li>
 * </ol>
 *
 * <p><b>能力缓存（计划分组 E）</b>：兜底阶梯的降级决策由 {@link RegionCapabilityCache} 按
 * {@code language.getID()} 惰性探测并缓存——PLAIN_TEXT_FALLBACK 直接短路返回 UNKNOWN（交由
 * RegionClassifier 走 PLAIN_TEXT 自适应）、COMMENTER_ONLY 跳过 token 层直接 Commenter、
 * TOKEN_ONLY 先 token 层未命中后仍 Commenter；能力未知（language 为 null）时不短路全量尝试。
 * PSI 主判恒先行且逐次判定，不缓存不短路。
 *
 * <p><b>语言无关</b>：仅使用平台通用接口，不引用任何语言专属 PSI 类，保持 CLion/Rider 兼容。
 *
 * <p><b>runIde 待实测项</b>（无法在纯 fixture 中验证）：
 * <ul>
 *   <li>C++/SQL/CSS 等真实语言插件是否用平台默认语义 key（DEFAULT_STRING/DEFAULT_LINE_COMMENT 等）
 *       着色其字符串/注释 token —— 自定义 key（如 JAVA_STRING）不会命中本层，属已知局限；</li>
 *   <li>charTyped 时刻 {@code EditorEx.getHighlighter()} 的 live 数据是否已包含刚插入字符的 token；</li>
 *   <li>XML attribute value 等注入场景是否仍全部由 InjectionHost 主判覆盖（本层不改变主判）。</li>
 * </ul>
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
     * 判定光标所在位置的上下文类型（无 editor，不启用 token 兜底层；能力缓存短路仍生效——
     * 对能力缓存判为 PLAIN_TEXT_FALLBACK / PSI_PRECISE 的语言直接返回 UNKNOWN，
     * 旧版此场景终态为 CODE，其余场景与旧版行为一致）。
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
        return resolve(psiFile, offset, document, project, null);
    }
    
    /**
     * 判定光标所在位置的上下文类型（带 editor，启用 SyntaxHighlighter token 兜底层）。
     *
     * @param psiFile 当前 PSI 文件（可为 null，此时直接返回 UNKNOWN）
     * @param offset  光标偏移
     * @param document 当前文档（Commenter 兜底时需要）
     * @param project 当前项目（token 层取高亮器时需要）
     * @param editor  当前编辑器（可为 null；非 null 且能力缓存不跳过 token 层时，主判未命中后启用 token 兜底层）
     * @return 上下文类型
     */
    @NotNull
    public static ContextType resolve(@Nullable PsiFile psiFile,
                                      int offset,
                                      @Nullable Document document,
                                      @Nullable Project project,
                                      @Nullable Editor editor) {
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
    
        // —— 兜底阶梯（token 层 + Commenter 层）：位于 injectionHost 主判之后 ——
        // 进入兜底路径前先查能力缓存（RegionCapabilityCache，按 language.getID() 惰性探测），
        // 降级阶梯（能力语义见 RegionCapabilityCache 类注释；PSI 主判恒先行、不缓存不短路）：
        //   PLAIN_TEXT_FALLBACK → 直接短路返回 UNKNOWN（token 与 Commenter 均探测为不可用，
        //     交由 RegionClassifier 走 PLAIN_TEXT 自适应），跳过 token 与 Commenter 尝试；
        //   COMMENTER_ONLY → 跳过 token 层，直接走 Commenter 兜底；
        //   TOKEN_ONLY → 先尝试 token 层，未命中后仍走 Commenter 兜底（并存语义：Commenter
        //     可用与否不影响本值，token 未命中照常降级）；
        //   PSI_PRECISE → 保留值，语义为“不应进入兜底路径”，与 PLAIN_TEXT_FALLBACK 同样短路；
        //   能力未知（null）→ 不短路、全量尝试（仅此时与旧版行为一致；能力已知的短路语言
        //     终态由旧版 CODE 变为 UNKNOWN，交由 RegionClassifier 走 PLAIN_TEXT 自适应）。
        RegionCapabilityCache.Capability capability = RegionCapabilityCache.capabilityFor(psiFile.getLanguage());
        if (shouldShortCircuitFallback(capability)) {
            return ContextType.UNKNOWN;
        }
        if (shouldTryTokenLayer(capability) && editor != null) {
            ContextType byToken = resolveByHighlighterTokens(editor, psiFile, offset, project);
            if (byToken != null) {
                return byToken;
            }
        }
    
        if (document != null) {
            if (isCommentByCommenter(psiFile, document, offset)) {
                return ContextType.COMMENT;
            }
        }
    
        return ContextType.CODE;
    }
    
    /**
     * token 兜底层：取「光标前字符所在 token」的高亮 key 并映射为上下文类型。
     *
     * <p>任何异常（高亮器不可用 / 语言无 SyntaxHighlighter 等）都静默返回 null，
     * 交由后续 Commenter 兜底与默认 CODE 路径处理，不影响主判定链；
     * {@link ProcessCanceledException} 为平台取消信号，原样上抛不吞并。
     *
     * @return COMMENT / STRING；token 层无法判定时返回 null
     */
    @Nullable
    private static ContextType resolveByHighlighterTokens(@NotNull Editor editor,
                                                          @NotNull PsiFile psiFile,
                                                          int offset,
                                                          @Nullable Project project) {
        try {
            EditorHighlighter highlighter = locateEditorHighlighter(editor, psiFile, project);
            if (highlighter == null) {
                return null;
            }
            HighlighterIterator iterator = locateTokenIterator(highlighter, offset);
            if (iterator == null) {
                return null;
            }
            IElementType tokenType = iterator.getTokenType();
            if (tokenType == null) {
                return null;
            }
            // 必须走 tokenType → getTokenHighlights 的 key 路线：
            // HighlighterIterator.getTextAttributes() 返回渲染用 TextAttributes（非 key），无法比对。
            // 映射器优先取「产生该 token 的同一个高亮器」自带映射器（与 tokenType 同源，多语言
            // 宿主文件的复合高亮器尤其如此），取不到时才回退按文件语言经工厂重查（存在错配局限）。
            SyntaxHighlighter mapper = mapperFor(highlighter, psiFile, project);
            return mapKeysToContext(mapper.getTokenHighlights(tokenType));
        } catch (ProcessCanceledException e) {
            // 平台取消信号必须原样抛出，不可吞并
            throw e;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 取 token 层使用的编辑器高亮器：live 优先、工厂备选。
     *
     * <p>{@code editor instanceof EditorEx} 时直接 {@code getHighlighter()}（零成本且数据实时）；
     * editor 非 {@link EditorEx}（如部分内嵌编辑器）时备选
     * {@link HighlighterFactory#createHighlighter(Project, VirtualFile)} 新建（virtualFile 为 null
     * 或创建异常则放弃 token 层）。
     */
    @Nullable
    private static EditorHighlighter locateEditorHighlighter(@NotNull Editor editor,
                                                             @NotNull PsiFile psiFile,
                                                             @Nullable Project project) {
        try {
            if (editor instanceof EditorEx) {
                return ((EditorEx) editor).getHighlighter();
            }
            if (project != null) {
                VirtualFile virtualFile = psiFile.getVirtualFile();
                if (virtualFile != null) {
                    return HighlighterFactory.createHighlighter(project, virtualFile);
                }
            }
            return null;
        } catch (ProcessCanceledException e) {
            // 平台取消信号必须原样抛出，不可吞并
            throw e;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 定位覆盖「光标前字符」的 token 迭代器。
     *
     * <p><b>定位语义</b>：键入字符位于 {@code [offset - 1, offset)}，token 层判定的是该字符的
     * 归属，故一律定位 {@code offset - 1} 处的 token（{@code createIterator(offset - 1)} 的
     * 标准语义即返回 {@code start() <= offset - 1 < end()} 的 token）——它必然包含键入字符；
     * 任何「起始为 offset」的 token 都不含键入字符，不得命中（此处曾因首查
     * {@code createIterator(offset)} 在 start==offset 时误命中光标后 token 而误判，已修正）。
     *
     * <p>{@code offset - 1} 恒小于文档长度（词法器覆盖全文本），定位安全；{@code offset <= 0}
     * 时无「光标前字符」，token 层无法判定，返回 null 跳过本层。
     */
    @Nullable
    private static HighlighterIterator locateTokenIterator(@NotNull EditorHighlighter highlighter,
                                                           int offset) {
        if (offset <= 0) {
            return null;
        }
        try {
            return highlighter.createIterator(offset - 1);
        } catch (ProcessCanceledException e) {
            // 平台取消信号必须原样抛出，不可吞并
            throw e;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 取 tokenType 的 key 映射器：优先「产生该 token 的同一个高亮器」自带映射器
     * （{@code LexerEditorHighlighter.getSyntaxHighlighter}，javap 203 核实存在；
     * {@code LayeredLexerEditorHighlighter} 多语言宿主复合高亮器继承之），tokenType 与
     * 映射器同源；取不到时回退按文件语言经 {@link SyntaxHighlighterFactory} 重查
     * （tokenType 与映射器可能错配，通常空数组静默退化，已知局限）。
     */
    @NotNull
    private static SyntaxHighlighter mapperFor(@NotNull EditorHighlighter highlighter,
                                               @NotNull PsiFile psiFile,
                                               @Nullable Project project) {
        if (highlighter instanceof LexerEditorHighlighter) {
            SyntaxHighlighter syntaxHighlighter =
                    ((LexerEditorHighlighter) highlighter).getSyntaxHighlighter();
            if (syntaxHighlighter != null) {
                return syntaxHighlighter;
            }
        }
        return SyntaxHighlighterFactory.getSyntaxHighlighter(psiFile.getLanguage(), project, psiFile.getVirtualFile());
    }
    
    /**
     * 纯逻辑：SyntaxHighlighter token 的 key 数组 → 上下文类型（可脱平台单测）。
     *
     * <p>映射规则：命中 LINE_COMMENT / BLOCK_COMMENT / DOC_COMMENT → COMMENT；命中 STRING → STRING。
     * <b>COMMENT 优先于 STRING</b>：docstring 等复合属性 token（可能同时叠加两类 key）
     * 取更保守的注释区归类。
     *
     * <p><b>已知局限</b>：仅命中平台默认语义 key（DEFAULT_STRING / DEFAULT_*_COMMENT，
     * 按 {@link TextAttributesKey#equals} 比较）；语言自定义 key（如 Java 的 JAVA_STRING）不命中，
     * 这类语言由 Commenter 兜底或默认 CODE 路径处理。命中与否的广度须 runIde 实测。
     *
     * @param keys token 的 key 数组（可为 null；容忍数组内 null 元素）
     * @return COMMENT / STRING；无法判定返回 null（非注释非字符串 token）
     */
    @Nullable
    static ContextType mapKeysToContext(@Nullable TextAttributesKey[] keys) {
        if (keys == null || keys.length == 0) {
            return null;
        }
        for (TextAttributesKey key : keys) {
            if (key == null) {
                continue;
            }
            if (key.equals(DefaultLanguageHighlighterColors.LINE_COMMENT)
                    || key.equals(DefaultLanguageHighlighterColors.BLOCK_COMMENT)
                    || key.equals(DefaultLanguageHighlighterColors.DOC_COMMENT)) {
                return ContextType.COMMENT;
            }
        }
        for (TextAttributesKey key : keys) {
            if (key != null && key.equals(DefaultLanguageHighlighterColors.STRING)) {
                return ContextType.STRING;
            }
        }
        return null;
    }

    /**
     * 纯逻辑：当前能力是否值得尝试 token 兜底层（可脱平台单测）。
     *
     * <p>能力未知（{@code null}，如 language 为 null）按“值得尝试”处理——保持旧行为全量尝试，
     * 由 {@code resolveByHighlighterTokens} 自身的静默失败兜住；{@link RegionCapabilityCache.Capability#TOKEN_ONLY}
     * （含 Commenter 并存语义）→ 尝试；其余能力（COMMENTER_ONLY / PLAIN_TEXT_FALLBACK / PSI_PRECISE）→ 跳过。
     *
     * @param capability 能力缓存值（可为 null）
     * @return true = 应进入 token 兜底层
     */
    static boolean shouldTryTokenLayer(@Nullable RegionCapabilityCache.Capability capability) {
        return capability == null
                || capability == RegionCapabilityCache.Capability.TOKEN_ONLY;
    }

    /**
     * 纯逻辑：当前能力是否应短路整个兜底路径（可脱平台单测）。
     *
     * <p>{@link RegionCapabilityCache.Capability#PLAIN_TEXT_FALLBACK}：token 层与 Commenter 层
     * 皆不可用，直接返回 UNKNOWN 交由 RegionClassifier 走 PLAIN_TEXT 自适应；
     * {@link RegionCapabilityCache.Capability#PSI_PRECISE}：保留值，语义为“不应进入兜底路径”。
     *
     * @param capability 能力缓存值（可为 null，null 不短路）
     * @return true = 兜底路径短路，resolve 直接返回 UNKNOWN
     */
    static boolean shouldShortCircuitFallback(@Nullable RegionCapabilityCache.Capability capability) {
        return capability == RegionCapabilityCache.Capability.PLAIN_TEXT_FALLBACK
                || capability == RegionCapabilityCache.Capability.PSI_PRECISE;
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
        if (blockPrefix != null && !blockPrefix.isEmpty()
                && blockSuffix != null && !blockSuffix.isEmpty()) {
            CharSequence before = document.getCharsSequence();
            int end = Math.min(offset, before.length());
            return isInsideBlockComment(before.subSequence(0, end), blockPrefix, blockSuffix);
        }

        return false;
    }

    /**
     * 纯逻辑：判断光标前文本是否处于一个<b>未闭合</b>的块注释内部。
     *
     * <p>从头扫描，遇到 {@code blockPrefix} 记为开、遇到 {@code blockSuffix} 记为闭；
     * 结束时若仍有未闭合的开标记，则光标位于块注释内。语言无关，仅依赖成对标记，
     * 覆盖 C/C++/SQL/XML 多行块注释的中间行（PSI 不可用时的兜底）。
     *
     * @param before      光标前文本（可为子序列视图）
     * @param blockPrefix 块注释起始标记
     * @param blockSuffix 块注释结束标记
     * @return true = 在块注释内
     */
    static boolean isInsideBlockComment(CharSequence before, String blockPrefix, String blockSuffix) {
        if (before == null || blockPrefix == null || blockPrefix.isEmpty()
                || blockSuffix == null || blockSuffix.isEmpty()) {
            return false;
        }
        int n = before.length();
        int pn = blockPrefix.length();
        int sn = blockSuffix.length();
        boolean open = false;
        int i = 0;
        while (i < n) {
            if (matchesAt(before, i, blockPrefix, pn)) {
                open = true;
                i += pn;
                continue;
            }
            if (open && matchesAt(before, i, blockSuffix, sn)) {
                open = false;
                i += sn;
                continue;
            }
            i++;
        }
        return open;
    }

    private static boolean matchesAt(CharSequence s, int i, String token, int tokenLen) {
        if (i + tokenLen > s.length()) {
            return false;
        }
        for (int k = 0; k < tokenLen; k++) {
            if (s.charAt(i + k) != token.charAt(k)) {
                return false;
            }
        }
        return true;
    }
}
