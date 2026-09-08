package com.haojiyou.cnchar.settings;

import com.haojiyou.cnchar.region.InputRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段2 单测：不可变快照 {@link CharAutoReplaceSettings.Snapshot} 的构建（脱 Application）。
 *
 * <p>{@code Snapshot.build(State)} 是 State 的纯函数；{@code new CharAutoReplaceSettings()} +
 * {@code loadState} + {@code isCandidateChar} 路径不触碰任何平台类型，可在纯 JUnit5 下验证
 * “旁路构建 + 单次 volatile 引用交换”的读一致性。
 */
class SettingsSnapshotTest {

    private static CharAutoReplaceSettings.State stateWithCustom(String from, String to) {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        state.customMappings.add(new MappingRule(from, to));
        return state;
    }

    @Test
    @DisplayName("候选位图：全角字符 / 精选 CJK 标点 → true")
    void candidatesForFullWidthAndCurated() {
        CharAutoReplaceSettings.Snapshot snap =
                CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
        assertTrue(snap.isCandidate('\uFF0C'), "，（全角）是候选");
        assertTrue(snap.isCandidate('\u3002'), "。（精选）是候选");
        assertTrue(snap.isCandidate('\u3001'), "、（精选）是候选");
        assertTrue(snap.isCandidate('\u2026'), "…（精选）是候选");
        assertTrue(snap.isCandidate('\u3000'), "表意空格是候选");
    }

    @Test
    @DisplayName("候选位图：普通 ASCII → false")
    void nonCandidatesForAscii() {
        CharAutoReplaceSettings.Snapshot snap =
                CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
        assertFalse(snap.isCandidate('a'));
        assertFalse(snap.isCandidate('1'));
        assertFalse(snap.isCandidate(' '));
        assertFalse(snap.isCandidate('\u4E2D'), "中（CJK 表意）非候选");
    }

    @Test
    @DisplayName("候选位图：自定义多字符键的首字符 → true")
    void candidateForMultiCharKeyFirstChar() {
        CharAutoReplaceSettings.Snapshot snap =
                CharAutoReplaceSettings.Snapshot.build(stateWithCustom("AB", "X"));
        assertTrue(snap.isCandidate('A'), "多字符键 AB 的首字符 A 是候选");
        assertTrue(snap.getMultiCharFirstChars().contains('A'));
        assertEquals("X", snap.getCharMap().get("AB"));
    }

    @Test
    @DisplayName("快照重建后读一致：改 State 再 build，charMap 反映新配置")
    void rebuildReflectsNewConfig() {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();
        CharAutoReplaceSettings.Snapshot before = CharAutoReplaceSettings.Snapshot.build(state);
        assertEquals(".", before.getCharMap().get("\u3002"), "初始：。→.（精选表）");

        // 用户新增自定义 。→DOT，旁路构建全新快照
        state.customMappings.add(new MappingRule("\u3002", "DOT"));
        CharAutoReplaceSettings.Snapshot after = CharAutoReplaceSettings.Snapshot.build(state);

        assertNotSame(before, after, "重建产生全新不可变快照实例");
        assertEquals("DOT", after.getCharMap().get("\u3002"), "自定义覆盖精选表");
        assertEquals(".", before.getCharMap().get("\u3002"), "旧快照不受影响（不可变）");
    }

    @Test
    @DisplayName("区策略快照：默认 CODE=ALWAYS、CONSOLE=NEVER、UNREACHABLE=NEVER")
    void regionModesInSnapshot() {
        CharAutoReplaceSettings.Snapshot snap =
                CharAutoReplaceSettings.Snapshot.build(new CharAutoReplaceSettings.State());
        assertEquals(RegionPolicy.ReplaceMode.ALWAYS, snap.getMode(InputRegion.CODE));
        assertEquals(RegionPolicy.ReplaceMode.ADAPTIVE, snap.getMode(InputRegion.COMMENT));
        assertEquals(RegionPolicy.ReplaceMode.NEVER, snap.getMode(InputRegion.CONSOLE));
        assertEquals(RegionPolicy.ReplaceMode.NEVER, snap.getMode(InputRegion.UNREACHABLE));
    }

    // ---- 经服务实例（无 Application）验证 volatile 快照交换读路径 ----

    @Test
    @DisplayName("服务实例：loadState 后 isCandidateChar 走 1 volatile 读 + 位图探测")
    void serviceInstanceReadPathWithoutApplication() {
        CharAutoReplaceSettings settings = new CharAutoReplaceSettings();
        // 默认快照即可判定
        assertTrue(settings.isCandidateChar('\uFF0C'));
        assertFalse(settings.isCandidateChar('a'));

        // 写入新 State → 旁路构建 + 单次 volatile 交换
        CharAutoReplaceSettings.State state = stateWithCustom("AB", "X");
        settings.loadState(state);

        assertTrue(settings.isCandidateChar('A'), "多字符键首字符成为候选");
        assertFalse(settings.isCandidateChar('a'));
        assertEquals("X", settings.getSnapshot().getCharMap().get("AB"));
        assertEquals(RegionPolicy.ReplaceMode.ALWAYS, settings.getSnapshot().getMode(InputRegion.CODE));
    }
}
