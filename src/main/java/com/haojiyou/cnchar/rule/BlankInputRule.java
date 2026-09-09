package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.core.EditorContext;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 空白输入过滤 —— 拒绝“无意义的空白输入”，但放行“属于候选字符的空白类字符”。
 *
 * <p><b>修复说明（U+3000 全角空格被误拦截 Bug）</b>：原实现用 {@code Character.isWhitespace(c)}
 * 一刀切拒绝所有空白字符，而 {@code Character.isWhitespace('\u3000')}（表意空格）为 {@code true}，
 * 导致本应转换为半角空格的<b>候选字符</b> {@code U+3000} 在规则链首节点即被 REJECT，永远到不了
 * {@code CharConverter}，设计要求的「{@code U+3000 → 半角空格}」因此失效。
 *
 * <p>事实上，普通半角空格 {@code U+0020} 等“真正无意义空白”本就不是候选字符，早在 handler 的
 * {@code isCandidateChar} O(1) 门控阶段即被过滤，根本不会进入规则链；因此这里对空白的过滤
 * 用候选位图精确表达即可：<b>仅当空白字符“不是候选字符”时才拒绝</b>，既保留了 BlankInputRule
 * 对无意义空白的原始过滤意图，又让 {@code U+3000} 等候选空白字符正常穿过链路到达转换引擎。
 *
 * @author : best.xu
 */
public class BlankInputRule implements ReplacementRule {

    private final CharAutoReplaceSettings.Snapshot snapshot;

    public BlankInputRule(@NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    @NotNull
    public Result check(@NotNull EditorContext ctx) {
        char c = ctx.getTypedChar();
        // 仅拒绝“非候选的空白字符”（真正无意义输入，如半角空格 U+0020、制表符、换行）；
        // 候选空白字符（如表意空格 U+3000）需放行至 CharConverter 转换为半角空格。
        if (Character.isWhitespace(c) && !snapshot.isCandidate(c)) {
            return Result.REJECT;
        }
        return Result.PASS;
    }
}
