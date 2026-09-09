package com.haojiyou.cnchar.core;

import com.intellij.lang.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段6 单测：{@link RegionCapabilityCache} 能力缓存（计划分组 E，脱平台、语言无关）。
 *
 * <p>覆盖路径分三层：
 * <ul>
 *   <li><b>注入路径</b>：缓存命中 / key 语义 / {@code clear()} 重探 / 探测异常防御，经包级
 *       {@code capabilityFor(Language, Function)} 注入计数探测函数验证（不触平台）；</li>
 *   <li><b>纯函数路径</b>：能力组合 {@code combine} 与降级阶梯决策
 *       {@code CommentContextResolver.shouldTryTokenLayer / shouldShortCircuitFallback} 直接断言；</li>
 *   <li><b>真实探测冒烟</b>：匿名 {@link Language} 实例走公开探测入口——未注册语言在平台未初始化
 *       环境下扩展点查询抛异常也被按不可用处理，与有平台时（占位 PlainSyntaxHighlighter / 无 Commenter）
 *       结果确定性一致，均归为 PLAIN_TEXT_FALLBACK。</li>
 * </ul>
 *
 * <p><b>语言实例约束（平台全局注册表陷阱）</b>：{@link Language} 为 abstract 且无抽象方法
 * （javap 203 复核），但其构造器按<b>具体 Class 键</b>做全局判重——
 * {@code ourRegisteredLanguages: Map<Class<? extends Language>, Language>} 执行
 * {@code put(getClass(), this)} 后非空即抛 {@code ImplementationConflictException}，
 * 即<b>同一具体 Class 全 JVM 只允许一个实例，与 ID 无关</b>（javap -c 字节码实证；与此前
 * COMMIT UserData Key 的身份语义同族：平台注册表按“身份”而非“名字”判重）。因此每个测试语言
 * 必须是<b>独立的具体类</b>（独立命名静态嵌套类，见类尾部）且<b>全 JVM 仅构造一次</b>
 * （静态 final 字段初始化点各构造一次）；禁止在共享 lambda/辅助方法内对同一匿名类按多 ID
 * 重复 new（曾致失败 4→5 例退化的教训）。重跑安全性：同一类加载器重跑时静态字段保留、
 * 不会重复构造（类初始化仅一次）；新类加载器重跑时嵌套类产生新 Class 键，Class 判重
 * 天然不冲突（单类加载器为 gradle test 默认形态）。「同 ID 共享缓存槽」用例用两个独立
 * 嵌套类，各自 {@code @Override getID()} 返回同一共享 ID。
 */
class RegionCapabilityCacheTest {

    @BeforeEach
    void resetCacheBefore() {
        RegionCapabilityCache.clear();
    }

    @AfterEach
    void resetCacheAfter() {
        RegionCapabilityCache.clear();
    }

    // —— 测试语言实例：每个语言一个独立命名嵌套类（不同 Class 键），静态 final 字段初始化点各构造一次 ——
    // 构造器按具体 Class 键全局判重（见类 javadoc），故「独立具体类 + 全 JVM 仅构造一次」是唯一安全形态；
    // 实例在类加载时构造一次，之后任意用例/任意顺序/重复执行均复用同一实例。
    
    /** {@link #secondQueryHitsCache} 用例语言（构造 ID: Test.CacheHit）。 */
    private static final Language LANG_CACHE_HIT = new LangCacheHit();
    
    /** {@link #distinctIdsProbeIndependently} 用例语言（构造 ID: Test.IdAlpha）。 */
    private static final Language LANG_ID_ALPHA = new LangIdAlpha();
    
    /** {@link #distinctIdsProbeIndependently} 用例语言（构造 ID: Test.IdBeta）。 */
    private static final Language LANG_ID_BETA = new LangIdBeta();
    
    /** {@link #clearForcesReprobe} 用例语言（构造 ID: Test.Clear）。 */
    private static final Language LANG_CLEAR = new LangClear();
    
    /** {@link #probeExceptionTreatedAsUnavailable} 用例语言（构造 ID: Test.ProbeThrows）。 */
    private static final Language LANG_PROBE_THROWS = new LangProbeThrows();
    
    /** {@link #realProbeOfUnregisteredLanguageFallsBack} 用例语言（构造 ID: Test.RealProbe）。 */
    private static final Language LANG_REAL_PROBE = new LangRealProbe();
    
    /** {@link #cacheKeyIsLanguageId} 双实例之一（构造 ID: Test.Rego.First，getID 覆写返回共享 ID）。 */
    private static final Language LANG_SHARED_ID_A = new LangSharedIdA();
    
    /** {@link #cacheKeyIsLanguageId} 双实例之二（构造 ID: Test.Rego.Second，getID 覆写返回共享 ID）。 */
    private static final Language LANG_SHARED_ID_B = new LangSharedIdB();

    @Test
    @DisplayName("能力组合：token 与 Commenter 并存 → 优先表达 TOKEN_ONLY（Commenter 未命中后仍照常尝试）")
    void combineMatrix() {
        assertEquals(RegionCapabilityCache.Capability.TOKEN_ONLY,
                RegionCapabilityCache.combine(true, true));
        assertEquals(RegionCapabilityCache.Capability.TOKEN_ONLY,
                RegionCapabilityCache.combine(true, false));
        assertEquals(RegionCapabilityCache.Capability.COMMENTER_ONLY,
                RegionCapabilityCache.combine(false, true));
        assertEquals(RegionCapabilityCache.Capability.PLAIN_TEXT_FALLBACK,
                RegionCapabilityCache.combine(false, false));
    }

    @Test
    @DisplayName("同 Language 二次查询命中缓存：探测函数幂等，仅执行一次")
    void secondQueryHitsCache() {
        Language language = LANG_CACHE_HIT;
        AtomicInteger probeCount = new AtomicInteger();
        Function<Language, RegionCapabilityCache.Capability> probe = lang -> {
            probeCount.incrementAndGet();
            return RegionCapabilityCache.Capability.TOKEN_ONLY;
        };

        assertEquals(RegionCapabilityCache.Capability.TOKEN_ONLY,
                RegionCapabilityCache.capabilityFor(language, probe));
        assertEquals(RegionCapabilityCache.Capability.TOKEN_ONLY,
                RegionCapabilityCache.capabilityFor(language, probe));
        assertEquals(RegionCapabilityCache.Capability.TOKEN_ONLY,
                RegionCapabilityCache.capabilityFor(language, probe));
        assertEquals(1, probeCount.get(), "重复查询必须命中缓存，探测函数只执行一次");
    }

    @Test
    @DisplayName("缓存 key 是 language.getID()：两实例覆写 getID 返回同 ID，共享缓存槽只探一次")
    void cacheKeyIsLanguageId() {
        // 两个独立嵌套类实例（不同 Class 键），getID() 均覆写返回共享 ID —— 验证缓存按 getID() 键入；
        // 各自静态 final 字段仅构造一次，不触发 Class 键判重冲突
        Language first = LANG_SHARED_ID_A;
        Language second = LANG_SHARED_ID_B;
        AtomicInteger probeCount = new AtomicInteger();
        Function<Language, RegionCapabilityCache.Capability> probe = lang -> {
            probeCount.incrementAndGet();
            return RegionCapabilityCache.Capability.COMMENTER_ONLY;
        };

        assertEquals(RegionCapabilityCache.Capability.COMMENTER_ONLY,
                RegionCapabilityCache.capabilityFor(first, probe));
        assertEquals(RegionCapabilityCache.Capability.COMMENTER_ONLY,
                RegionCapabilityCache.capabilityFor(second, probe));
        assertEquals(1, probeCount.get(), "同 ID 不同实例应命中同一缓存槽（key = getID()，非实例身份）");
    }

    @Test
    @DisplayName("不同 ID 各自探测、互不串值：缓存槽按 ID 隔离")
    void distinctIdsProbeIndependently() {
        Language alpha = LANG_ID_ALPHA;
        Language beta = LANG_ID_BETA;
        AtomicInteger probeCount = new AtomicInteger();
        Function<Language, RegionCapabilityCache.Capability> probe = lang -> {
            probeCount.incrementAndGet();
            return lang == alpha
                    ? RegionCapabilityCache.Capability.TOKEN_ONLY
                    : RegionCapabilityCache.Capability.COMMENTER_ONLY;
        };

        assertEquals(RegionCapabilityCache.Capability.TOKEN_ONLY,
                RegionCapabilityCache.capabilityFor(alpha, probe));
        assertEquals(RegionCapabilityCache.Capability.COMMENTER_ONLY,
                RegionCapabilityCache.capabilityFor(beta, probe));
        assertEquals(RegionCapabilityCache.Capability.TOKEN_ONLY,
                RegionCapabilityCache.capabilityFor(alpha, probe));
        assertEquals(2, probeCount.get(), "不同 ID 各探一次，同 ID 命中各自缓存槽");
    }

    @Test
    @DisplayName("clear() 清空缓存：再次查询触发重新探测")
    void clearForcesReprobe() {
        Language language = LANG_CLEAR;
        AtomicInteger probeCount = new AtomicInteger();
        Function<Language, RegionCapabilityCache.Capability> probe = lang -> {
            probeCount.incrementAndGet();
            return RegionCapabilityCache.Capability.PLAIN_TEXT_FALLBACK;
        };

        RegionCapabilityCache.capabilityFor(language, probe);
        assertEquals(1, probeCount.get());
        RegionCapabilityCache.clear();
        RegionCapabilityCache.capabilityFor(language, probe);
        assertEquals(2, probeCount.get(), "clear() 后必须重新探测");
    }

    @Test
    @DisplayName("探测异常按不可用处理：归一化为 PLAIN_TEXT_FALLBACK 且入缓存，不反复触发")
    void probeExceptionTreatedAsUnavailable() {
        Language language = LANG_PROBE_THROWS;
        AtomicInteger probeCount = new AtomicInteger();
        Function<Language, RegionCapabilityCache.Capability> probe = lang -> {
            probeCount.incrementAndGet();
            throw new IllegalStateException("模拟扩展点查询异常");
        };

        assertEquals(RegionCapabilityCache.Capability.PLAIN_TEXT_FALLBACK,
                RegionCapabilityCache.capabilityFor(language, probe));
        assertEquals(RegionCapabilityCache.Capability.PLAIN_TEXT_FALLBACK,
                RegionCapabilityCache.capabilityFor(language, probe));
        assertEquals(1, probeCount.get(), "异常归一化结果应入缓存，避免每次击键反复抛异常探测");
    }

    @Test
    @DisplayName("null 语言守卫：capabilityFor(null) → null（调用方按不短路全量尝试处理）")
    void nullLanguageReturnsNull() {
        assertNull(RegionCapabilityCache.capabilityFor(null));
    }

    @Test
    @DisplayName("真实探测冒烟：未注册语言（无高亮器无 Commenter）→ PLAIN_TEXT_FALLBACK")
    void realProbeOfUnregisteredLanguageFallsBack() {
        Language language = LANG_REAL_PROBE;
        assertEquals(RegionCapabilityCache.Capability.PLAIN_TEXT_FALLBACK,
                RegionCapabilityCache.capabilityFor(language));
    }

    @Test
    @DisplayName("降级阶梯：PLAIN_TEXT_FALLBACK / PSI_PRECISE 短路；TOKEN_ONLY 与能力未知尝试 token 层")
    void ladderDecisions() {
        // 短路判定：仅两者皆不可用（或保留值 PSI_PRECISE）短路；null 不短路
        assertTrue(CommentContextResolver.shouldShortCircuitFallback(
                RegionCapabilityCache.Capability.PLAIN_TEXT_FALLBACK));
        assertTrue(CommentContextResolver.shouldShortCircuitFallback(
                RegionCapabilityCache.Capability.PSI_PRECISE));
        assertFalse(CommentContextResolver.shouldShortCircuitFallback(
                RegionCapabilityCache.Capability.TOKEN_ONLY));
        assertFalse(CommentContextResolver.shouldShortCircuitFallback(
                RegionCapabilityCache.Capability.COMMENTER_ONLY));
        assertFalse(CommentContextResolver.shouldShortCircuitFallback(null));

        // token 层尝试判定：能力未知（null，如 language 为 null）与 TOKEN_ONLY 尝试，其余跳过
        assertTrue(CommentContextResolver.shouldTryTokenLayer(null));
        assertTrue(CommentContextResolver.shouldTryTokenLayer(
                RegionCapabilityCache.Capability.TOKEN_ONLY));
        assertFalse(CommentContextResolver.shouldTryTokenLayer(
                RegionCapabilityCache.Capability.COMMENTER_ONLY));
        assertFalse(CommentContextResolver.shouldTryTokenLayer(
                RegionCapabilityCache.Capability.PLAIN_TEXT_FALLBACK));
        assertFalse(CommentContextResolver.shouldTryTokenLayer(
                RegionCapabilityCache.Capability.PSI_PRECISE));
    }

    // —— 测试语言的具体类：每个 ID 一个独立命名静态嵌套类 ——
    // Language 构造器按「具体 Class」键全局判重（ourRegisteredLanguages: Map<Class<? extends Language>, Language>，
    // javap -c 字节码实证）：同一具体 Class 全 JVM 只允许一个实例，与 ID 无关。嵌套类互为不同 Class，
    // 天然满足判重；静态 final 字段（见类头部）保证每个类仅构造一次。

    /** 测试语言：构造 ID Test.CacheHit（secondQueryHitsCache 用例）。 */
    private static class LangCacheHit extends Language {
        LangCacheHit() {
            super("Test.CacheHit");
        }
    }

    /** 测试语言：构造 ID Test.IdAlpha（distinctIdsProbeIndependently 用例）。 */
    private static class LangIdAlpha extends Language {
        LangIdAlpha() {
            super("Test.IdAlpha");
        }
    }

    /** 测试语言：构造 ID Test.IdBeta（distinctIdsProbeIndependently 用例）。 */
    private static class LangIdBeta extends Language {
        LangIdBeta() {
            super("Test.IdBeta");
        }
    }

    /** 测试语言：构造 ID Test.Clear（clearForcesReprobe 用例）。 */
    private static class LangClear extends Language {
        LangClear() {
            super("Test.Clear");
        }
    }

    /** 测试语言：构造 ID Test.ProbeThrows（probeExceptionTreatedAsUnavailable 用例）。 */
    private static class LangProbeThrows extends Language {
        LangProbeThrows() {
            super("Test.ProbeThrows");
        }
    }

    /** 测试语言：构造 ID Test.RealProbe（realProbeOfUnregisteredLanguageFallsBack 用例）。 */
    private static class LangRealProbe extends Language {
        LangRealProbe() {
            super("Test.RealProbe");
        }
    }

    /** 测试语言：构造 ID Test.Rego.First，getID() 覆写返回共享 ID Test.SharedId（cacheKeyIsLanguageId 用例实例 A）。 */
    private static class LangSharedIdA extends Language {
        LangSharedIdA() {
            super("Test.Rego.First");
        }

        @Override
        public String getID() {
            return "Test.SharedId";
        }
    }

    /** 测试语言：构造 ID Test.Rego.Second，getID() 覆写返回共享 ID Test.SharedId（cacheKeyIsLanguageId 用例实例 B）。 */
    private static class LangSharedIdB extends Language {
        LangSharedIdB() {
            super("Test.Rego.Second");
        }

        @Override
        public String getID() {
            return "Test.SharedId";
        }
    }
}
