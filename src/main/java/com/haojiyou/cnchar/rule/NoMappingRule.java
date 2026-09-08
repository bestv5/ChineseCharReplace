package com.haojiyou.cnchar.rule;

import com.haojiyou.cnchar.core.EditorContext;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import org.jetbrains.annotations.NotNull;

/**
 * 描述: 非候选字符过滤 —— 不在候选位图中的字符直接拒绝（O(1) 零分配门控）。
 *
 * @author : best.xu
 */
public class NoMappingRule implements ReplacementRule {

    private final CharAutoReplaceSettings.Snapshot snapshot;

    public NoMappingRule(@NotNull CharAutoReplaceSettings.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    @NotNull
    public Result check(@NotNull EditorContext ctx) {
        if (!snapshot.isCandidate(ctx.getTypedChar())) {
            return Result.REJECT;
        }
        return Result.PASS;
    }
}
