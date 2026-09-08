package com.haojiyou.cnchar.settings;

import com.haojiyou.cnchar.region.InputRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段2 单测：{@link LegacyConfigMigrator} 的<b>纯迁移函数</b>（脱平台，不触碰 PropertiesComponent）。
 *
 * <p>覆盖两分支（等于旧默认串 → 采用新默认、{@code 、→,} 生效；自定义串 → 忠实携带成对映射，含用户可能的
 * {@code 、→/}）、showHint、COMMENT 区三态、以及解析对数/trim 与阶段0 一致。
 */
class LegacyConfigMigratorTest {

    private static boolean hasRule(List<MappingRule> list, String from, String to) {
        for (MappingRule r : list) {
            if (from.equals(r.from) && to.equals(r.to)) {
                return true;
            }
        }
        return false;
    }

    // ---- (a) 等于旧默认串 / 缺省 → 采用新默认，customMappings 空，、→, 生效 ----

    @Test
    @DisplayName("(a) 传入旧 DEFAULT_STRING → customMappings 空，采用新默认")
    void legacyDefaultAdoptsNewDefault() {
        CharAutoReplaceSettings.State state =
                LegacyConfigMigrator.migrate(LegacyConfigMigrator.LEGACY_DEFAULT_STRING, null, null);
        assertTrue(state.customMappings.isEmpty(), "从未自定义 → 自定义表留空");
        // 快照（层2 精选表）里 、→,（修正生效）
        CharAutoReplaceSettings.Snapshot snap = CharAutoReplaceSettings.Snapshot.build(state);
        assertEquals(",", snap.getCharMap().get("\u3001"), "、 应为半角逗号");
        assertFalse("/".equals(snap.getCharMap().get("\u3001")), "、 不再是 /");
    }

    @Test
    @DisplayName("(a) configStr 缺省(null) → 同样采用新默认，customMappings 空")
    void absentConfigAdoptsNewDefault() {
        CharAutoReplaceSettings.State state = LegacyConfigMigrator.migrate(null, null, null);
        assertTrue(state.customMappings.isEmpty());
        assertEquals(",", CharAutoReplaceSettings.Snapshot.build(state).getCharMap().get("\u3001"));
    }

    // ---- (b) 自定义串 → 忠实携带成对映射（含用户可能的 、→/） ----

    @Test
    @DisplayName("(b) 自定义串 → customMappings 忠实携带成对映射（、→/ 保留）")
    void customStringFaithfullyCarried() {
        // 用户自定义：、→/、中→Z（换行成对串）
        String custom = "\u3001\n/\n\u4E2D\nZ";
        CharAutoReplaceSettings.State state = LegacyConfigMigrator.migrate(custom, null, null);
        assertEquals(2, state.customMappings.size());
        assertTrue(hasRule(state.customMappings, "\u3001", "/"), "忠实保留用户的 、→/");
        assertTrue(hasRule(state.customMappings, "\u4E2D", "Z"));
        // 自定义优先级最高：快照合并后 、→/ 覆盖精选表的 ,
        assertEquals("/", CharAutoReplaceSettings.Snapshot.build(state).getCharMap().get("\u3001"));
    }

    // ---- (c) showHint ← msgShowRaw ----

    @Test
    @DisplayName("(c) msgShowRaw true/false/缺省 → showHint")
    void showHintMigration() {
        assertTrue(LegacyConfigMigrator.migrate(null, "true", null).showHint);
        assertFalse(LegacyConfigMigrator.migrate(null, "false", null).showHint);
        assertFalse(LegacyConfigMigrator.migrate(null, null, null).showHint, "缺省 → false");
    }

    // ---- (d) COMMENT 区模式 ← replaceInCommentRaw ----

    @Test
    @DisplayName("(d) replaceInCommentRaw 缺省→ADAPTIVE、true→ALWAYS、false→NEVER")
    void commentModeMigration() {
        assertEquals(RegionPolicy.ReplaceMode.ADAPTIVE,
                LegacyConfigMigrator.migrate(null, null, null).commentMode, "键缺省 → 新默认 ADAPTIVE");
        assertEquals(RegionPolicy.ReplaceMode.ALWAYS,
                LegacyConfigMigrator.migrate(null, null, "true").commentMode, "显式 true → ALWAYS");
        assertEquals(RegionPolicy.ReplaceMode.NEVER,
                LegacyConfigMigrator.migrate(null, null, "false").commentMode, "显式 false → NEVER");
    }

    // ---- (e) 解析对数 / trim 与阶段0 一致 ----

    @Test
    @DisplayName("(e) token 两端空白被 trim")
    void tokensAreTrimmed() {
        CharAutoReplaceSettings.State state = LegacyConfigMigrator.migrate("  \uFF0C  \n  ,  ", null, null);
        assertEquals(1, state.customMappings.size());
        assertTrue(hasRule(state.customMappings, "\uFF0C", ","), "，→, 且两端空白被 trim");
    }

    @Test
    @DisplayName("(e) 奇数 token 时最后一项被丢弃（length/2 截断），与阶段0 一致")
    void oddTokenCountDropsLast() {
        CharAutoReplaceSettings.State state = LegacyConfigMigrator.migrate("a\nb\nc", null, null);
        assertEquals(1, state.customMappings.size());
        assertTrue(hasRule(state.customMappings, "a", "b"));
        assertFalse(hasRule(state.customMappings, "c", null));
    }

    @Test
    @DisplayName("(e) 其它默认区策略未受迁移影响（保持 State 默认）")
    void otherRegionDefaultsUntouched() {
        CharAutoReplaceSettings.State state = LegacyConfigMigrator.migrate(null, null, null);
        assertEquals(RegionPolicy.ReplaceMode.ALWAYS, state.modeOf(InputRegion.CODE));
        assertEquals(RegionPolicy.ReplaceMode.NEVER, state.modeOf(InputRegion.CONSOLE));
        assertEquals(RegionPolicy.ReplaceMode.ADAPTIVE, state.modeOf(InputRegion.STRING));
    }
}
