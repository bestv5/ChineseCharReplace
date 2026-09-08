package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.common.StrUtil;
import com.haojiyou.cnchar.core.EditorContext;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 空白输入过滤 —— 输入为空或纯空白时拒绝。
 *
 * @author : best.xu
 */
public class BlankInputRule implements ReplacementRule {

    @Override
    @NotNull
    public Result check(@NotNull EditorContext ctx) {
        char c = ctx.getTypedChar();
        if (Character.isWhitespace(c)) {
            return Result.REJECT;
        }
        return Result.PASS;
    }
}
