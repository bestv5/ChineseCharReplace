package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.core.EditorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 描述: 替换规则链接口 —— 前置过滤链的每个节点。
 *
 * <p>每个规则返回 {@link Result}：
 * <ul>
 *   <li>{@link Result#PASS}：本规则通过，继续下一个规则</li>
 *   <li>{@link Result#REJECT}：本规则拒绝，整个管线终止（不替换）</li>
 * </ul>
 *
 * @author : best.xu
 */
public interface ReplacementRule {

    /**
     * 规则判定结果。
     */
    enum Result {
        PASS,
        REJECT
    }

    /**
     * 判定是否通过。
     *
     * @param ctx 击键上下文
     * @return PASS 继续；REJECT 终止
     */
    @NotNull
    Result check(@NotNull EditorContext ctx);
}
