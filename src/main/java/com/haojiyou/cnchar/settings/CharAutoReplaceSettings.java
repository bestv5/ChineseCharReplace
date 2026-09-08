package com.haojiyou.cnchar.settings;

import com.haojiyou.cnchar.convert.CharConverter;
import com.haojiyou.cnchar.region.InputRegion;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 描述: 新配置模型 —— 应用级 {@link PersistentStateComponent} + <b>不可变 volatile 快照</b>（性能关键）。
 *
 * <p><b>持久化</b>：{@code @State(name="CharAutoReplaceSettings", storages=@Storage("charAutoReplace.xml"))}，
 * 注册为 applicationService（plugin.xml 附加式新增，用 {@code getService(类)} 取，无需 id）。
 *
 * <p><b>读路径（热）</b>：{@link #isCandidateChar(char)} = <b>1 次 volatile 读</b>（把快照取入局部变量）
 * + <b>1 次数组位图探测</b>，零分配、无锁。绝大多数 ASCII 击键在此立即判否。
 *
 * <p><b>写路径（冷，罕见）</b>：{@link #loadState} / 配置 apply 时，<b>旁路构建全新不可变 {@link Snapshot}</b>，
 * 再<b>单次 volatile 引用交换</b>；绝不原地 clear+重填（消除旧 {@code ReplaceCharConfig} 公有可变静态字段的 torn-read）。
 *
 * <p>{@link Snapshot#build(State)} 是 {@link State} 的<b>纯函数</b>，可脱离 Application 单测。
 *
 * @author : best.xu
 */
@com.intellij.openapi.components.State(
        name = "CharAutoReplaceSettings",
        storages = @Storage("charAutoReplace.xml")
)
public final class CharAutoReplaceSettings
        implements PersistentStateComponent<CharAutoReplaceSettings.State> {

    /** 当前持久化状态（仅在写路径替换引用）。 */
    private volatile State myState = new State();

    /** 运行期只读快照：不可变，volatile 保证跨线程可见与安全发布。 */
    private volatile Snapshot snapshot = Snapshot.build(myState);

    /** 迁移是否已尝试成功（成功后置 true，避免每次 getInstance 重复读旧 KEY）。 */
    private volatile boolean migrationDone = false;

    /**
     * 取应用级服务实例，并确保旧配置一次性迁移已尝试。
     */
    public static CharAutoReplaceSettings getInstance() {
        CharAutoReplaceSettings settings =
                ApplicationManager.getApplication().getService(CharAutoReplaceSettings.class);
        settings.ensureMigrated();
        return settings;
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        // 旁路构建全新不可变快照，再单次 volatile 引用交换（写路径，罕见）。
        State safe = state == null ? new State() : state;
        this.myState = safe;
        this.snapshot = Snapshot.build(safe);
    }

    /** 当前只读快照（供 Executor / shim / 转换器构建使用）。 */
    public Snapshot getSnapshot() {
        return snapshot;
    }

    /**
     * O(1) 候选门控：<b>1 次 volatile 读 + 1 次数组位图探测，零分配、无锁</b>。
     * 非候选字符（绝大多数 ASCII 击键）在此立即判否。
     */
    public boolean isCandidateChar(char c) {
        Snapshot s = this.snapshot;   // 单次 volatile 读入局部变量
        return s.candidate[c];        // 数组下标探测，O(1)、零分配
    }

    /**
     * 基于当前快照的合并映射表构建转换器（层1 自定义 + 层2 精选已合并；层3 全角偏移为算术，不入表）。
     */
    public CharConverter createConverter() {
        return new CharConverter(snapshot.getCharMap());
    }

    /**
     * 触发一次性旧配置迁移（幂等）。仅当迁移成功后置 {@link #migrationDone}，失败则下次 getInstance 重试。
     */
    void ensureMigrated() {
        if (migrationDone) {
            return;
        }
        synchronized (this) {
            if (migrationDone) {
                return;
            }
            migrationDone = LegacyConfigMigrator.ensureMigrated(this);
        }
    }

    // ==================================================================================
    // 可序列化 State（XmlSerializer：public 字段 + public 无参构造）
    // ==================================================================================

    /**
     * 持久化状态。<b>默认值</b>：CODE=ALWAYS、COMMENT=ADAPTIVE、STRING=ADAPTIVE、COMMIT=ADAPTIVE、
     * CONSOLE=NEVER、PLAIN_TEXT=ADAPTIVE、showHint=false、migrated=false、customMappings 空。
     */
    public static class State {
        /** 用户自定义映射（最高优先级）。可为单字符或多字符键。 */
        public List<MappingRule> customMappings = new ArrayList<>();

        /** 各区三态策略（public 枚举字段，便于 XmlSerializer 按名序列化）。 */
        public RegionPolicy.ReplaceMode codeMode = RegionPolicy.ReplaceMode.ALWAYS;
        public RegionPolicy.ReplaceMode commentMode = RegionPolicy.ReplaceMode.ADAPTIVE;
        public RegionPolicy.ReplaceMode stringMode = RegionPolicy.ReplaceMode.ADAPTIVE;
        public RegionPolicy.ReplaceMode commitMode = RegionPolicy.ReplaceMode.ADAPTIVE;
        public RegionPolicy.ReplaceMode consoleMode = RegionPolicy.ReplaceMode.NEVER;
        public RegionPolicy.ReplaceMode plainTextMode = RegionPolicy.ReplaceMode.ADAPTIVE;

        /** 是否显示替换提示。 */
        public boolean showHint = false;

        /** 迁移完成标志：仅当旧配置成功迁移入新 State 后置 true。 */
        public boolean migrated = false;

        /** 取某区域的策略模式；UNREACHABLE 恒 NEVER。 */
        public RegionPolicy.ReplaceMode modeOf(InputRegion region) {
            if (region == null) {
                return RegionPolicy.ReplaceMode.NEVER;
            }
            switch (region) {
                case CODE:
                    return codeMode;
                case COMMENT:
                    return commentMode;
                case STRING:
                    return stringMode;
                case COMMIT:
                    return commitMode;
                case CONSOLE:
                    return consoleMode;
                case PLAIN_TEXT:
                    return plainTextMode;
                case UNREACHABLE:
                default:
                    return RegionPolicy.ReplaceMode.NEVER;
            }
        }
    }

    // ==================================================================================
    // 不可变快照（State 的纯函数产物）
    // ==================================================================================

    /**
     * 运行期只读、不可变的派生数据。由 {@link #build(State)} 一次性构建后经 volatile 发布，之后绝不修改。
     */
    public static final class Snapshot {

        /**
         * 候选位图：直接数组下标探测，O(1)、零分配。
         *
         * <p><b>内存权衡</b>：{@code boolean[65536]} ≈ 64KB/快照。快照重建极罕见（仅配置写入时），
         * 旧快照随后被 GC，可接受；相比 {@code BitSet} 省去方法调用与位运算/边界检查，读路径更快。
         * 数组内容在构造期写满、发布后只读；volatile 交换保证安全发布。
         */
        final boolean[] candidate = new boolean[65536];

        /** 多字符自定义键的首字符集合（供 Executor 判断是否值得尝试尾部兜底匹配）。 */
        private final Set<Character> multiCharFirstChars;

        /** 冻结的合并映射表：自定义表（层1，覆盖）+ 精选 CJK 表（层2）。全角偏移是算术，不入表。 */
        private final Map<String, String> charMap;

        /** 各区策略（不可变 EnumMap 视图）。 */
        private final Map<InputRegion, RegionPolicy.ReplaceMode> regionModes;

        /** 是否显示替换提示（来自 State.showHint，不可变）。 */
        private final boolean showHint;

        private Snapshot(Set<Character> multiCharFirstChars,
                         Map<String, String> charMap,
                         Map<InputRegion, RegionPolicy.ReplaceMode> regionModes,
                         boolean showHint) {
            this.multiCharFirstChars = multiCharFirstChars;
            this.charMap = charMap;
            this.regionModes = regionModes;
            this.showHint = showHint;
        }

        /**
         * 纯函数：State → 不可变 Snapshot（不触碰 Application，可脱平台单测）。
         */
        public static Snapshot build(State state) {
            State s = state == null ? new State() : state;

            // --- 合并 charMap：层2 精选 CJK 表打底，层1 自定义表覆盖 ---
            Map<String, String> merged = new HashMap<>();
            for (Map.Entry<Character, String> e : CharConverter.curatedTable().entrySet()) {
                merged.put(String.valueOf(e.getKey()), e.getValue());
            }
            Set<Character> multiFirst = new HashSet<>();
            if (s.customMappings != null) {
                for (MappingRule rule : s.customMappings) {
                    if (rule == null || rule.from == null || rule.from.isEmpty()) {
                        continue;
                    }
                    merged.put(rule.from, rule.to == null ? "" : rule.to);
                    if (rule.from.length() > 1) {
                        multiFirst.add(rule.from.charAt(0));
                    }
                }
            }

            // --- 候选位图 ---
            Snapshot snapshot = new Snapshot(
                    Collections.unmodifiableSet(multiFirst),
                    Collections.unmodifiableMap(merged),
                    buildRegionModes(s),
                    s.showHint);
            boolean[] bits = snapshot.candidate;
            // 全角区 0xFF01–0xFF5E
            for (int c = CharConverter.fullWidthStart(); c <= CharConverter.fullWidthEnd(); c++) {
                bits[c] = true;
            }
            // 表意空格 U+3000
            bits[CharConverter.ideographicSpace()] = true;
            // 精选 CJK 标点各字符
            for (Character ch : CharConverter.curatedTable().keySet()) {
                bits[ch] = true;
            }
            // 每个自定义键（含多字符键）的首字符
            for (String key : merged.keySet()) {
                if (!key.isEmpty()) {
                    bits[key.charAt(0)] = true;
                }
            }
            return snapshot;
        }

        private static Map<InputRegion, RegionPolicy.ReplaceMode> buildRegionModes(State s) {
            EnumMap<InputRegion, RegionPolicy.ReplaceMode> modes = new EnumMap<>(InputRegion.class);
            for (InputRegion r : InputRegion.values()) {
                modes.put(r, s.modeOf(r));
            }
            return Collections.unmodifiableMap(modes);
        }

        /** 位图探测：O(1)、零分配。 */
        public boolean isCandidate(char c) {
            return candidate[c];
        }

        /** 冻结的合并映射表（只读）。 */
        public Map<String, String> getCharMap() {
            return charMap;
        }

        /** 多字符自定义键首字符集合（只读）。 */
        public Set<Character> getMultiCharFirstChars() {
            return multiCharFirstChars;
        }

        /** 取某区域策略；未知区域返回 NEVER。 */
        public RegionPolicy.ReplaceMode getMode(InputRegion region) {
            RegionPolicy.ReplaceMode m = regionModes.get(region);
            return m == null ? RegionPolicy.ReplaceMode.NEVER : m;
        }

        /** 是否显示替换提示。 */
        public boolean isShowHint() {
            return showHint;
        }
    }
}
