package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.core.EditorContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述: 前置过滤规则链 —— 按顺序执行各规则，任一 REJECT 则整体拒绝。
 *
 * <p>默认规则顺序：
 * <ol>
 *   <li>{@link BlankInputRule}：空白输入过滤</li>
 *   <li>{@link NoMappingRule}：非候选字符过滤</li>
 *   <li>{@link InputLengthRule}：输入长度过滤</li>
 * </ol>
 *
 * @author : best.xu
 */
public class ReplacementRuleChain {

    private final List<ReplacementRule> rules;

    public ReplacementRuleChain(@NotNull List<ReplacementRule> rules) {
        this.rules = new ArrayList<>(rules);
    }

    /**
     * 创建默认规则链。
     */
    @NotNull
    public static ReplacementRuleChain defaultChain(
            @NotNull com.haojiyou.cnchar.settings.CharAutoReplaceSettings.Snapshot snapshot) {
        List<ReplacementRule> rules = new ArrayList<>();
        rules.add(new BlankInputRule(snapshot));
        rules.add(new NoMappingRule(snapshot));
        rules.add(new InputLengthRule());
        return new ReplacementRuleChain(rules);
    }

    /**
     * 执行规则链。
     *
     * @param ctx 击键上下文
     * @return true = 全部通过（应继续后续处理）；false = 任一拒绝（应终止）
     */
    public boolean checkAll(@NotNull EditorContext ctx) {
        for (ReplacementRule rule : rules) {
            if (rule.check(ctx) == ReplacementRule.Result.REJECT) {
                return false;
            }
        }
        return true;
    }
}
