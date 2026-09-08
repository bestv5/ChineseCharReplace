package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.core.EditorContext;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 输入长度过滤 —— 单次输入长度超过上限时拒绝（防止 IME 组合输入等异常场景）。
 *
 * <p>当前实现：typedHandler 每次只传入单字符，故此规则恒 PASS。
 * 预留接口供未来扩展（如批量粘贴场景）。
 *
 * @author : best.xu
 */
public class InputLengthRule implements ReplacementRule {

    @Override
    @NotNull
    public Result check(@NotNull EditorContext ctx) {
        return Result.PASS;
    }
}
