package com.haojiyou.cnchar.core;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 描述: 按 Language 的兜底能力缓存（计划分组 E）—— application 级单例，
 * key = {@code language.getID()}，惰性探测（首次遇到某语言时计算并缓存）。
 *
 * <p><b>目的与能力语义</b>：缓存"某语言的兜底层是否值得尝试"，避免每次击键重复做
 * {@code SyntaxHighlighterFactory} / {@code LanguageCommenters} 的扩展点查询。
 * {@link Capability} 表达的是<b>最高优先的可用兜底层</b>，而非"独占"：
 * <ol>
 *   <li>{@link Capability#PSI_PRECISE}：<b>保留值</b>（当前探测不产生）——预留"语言 PSI 对
 *       注释/字符串语义完备、不应进入兜底路径"的语义，供未来语言元数据扩展；</li>
 *   <li>{@link Capability#TOKEN_ONLY}：SyntaxHighlighter token 兜底层可用。<b>并存语义</b>：
 *       若 Commenter 亦可用，仍归入本值（优先表达 token 可用），Commenter 层在 token 未命中后照常尝试；</li>
 *   <li>{@link Capability#COMMENTER_ONLY}：仅 Commenter 可用 → 跳过 token 层；</li>
 *   <li>{@link Capability#PLAIN_TEXT_FALLBACK}：两者皆不可用 → 兜底路径整体短路（resolve 返回
 *       UNKNOWN，交由 RegionClassifier 走 PLAIN_TEXT 自适应）。</li>
 * </ol>
 *
 * <p><b>不缓存 PSI 主判</b>：PSI 主判恒先行且逐次判定（与语言能力无关），缓存只作用于兜底阶梯。
 *
 * <p><b>探测语义偏差说明</b>：公共 {@code SyntaxHighlighterFactory.getSyntaxHighlighter(language, null, null)}
 * 返回类型为 {@code @NotNull}（javap + 203 源码语义核实：未注册语言退化为 {@link PlainSyntaxHighlighter}
 * 占位，不会返回 null），故字面上的 {@code != null} 探测恒真。本实现以
 * {@code !(highlighter instanceof PlainSyntaxHighlighter)} 表达"token 层值得尝试"：
 * 占位高亮器不携带任何语义 key（对 {@code getTokenHighlights} 无贡献），尝试它没有意义。
 * 探测全程 try/catch（扩展点查询在部分环境可能抛异常），异常一律按不可用处理；
 * {@link ProcessCanceledException} 为平台取消信号，原样上抛不吞并、也不建立缓存条目。
 *
 * @author : best.xu
 */
public final class RegionCapabilityCache {

    /** 语言兜底能力（表达最高优先的可用兜底层，非独占——并存语义见类 javadoc）。 */
    public enum Capability {
        PSI_PRECISE,
        TOKEN_ONLY,
        COMMENTER_ONLY,
        PLAIN_TEXT_FALLBACK
    }

    /** application 级缓存：key = language.getID()，value = 能力值。 */
    private static final Map<String, Capability> CACHE = new ConcurrentHashMap<>();

    private RegionCapabilityCache() {
    }

    /**
     * 取某语言的兜底能力（惰性探测 + 缓存，首次遇到时计算并写入 {@code ConcurrentHashMap}）。
     *
     * @param language 语言实例（可为 null → 返回 null，调用方按"不短路、走完整兜底阶梯"处理）
     * @return 能力值；language 为 null 时返回 null
     */
    @Nullable
    public static Capability capabilityFor(@Nullable Language language) {
        if (language == null) {
            return null;
        }
        return capabilityFor(language, RegionCapabilityCache::probeCapability);
    }

    /**
     * 缓存逻辑与探测函数解耦的内部入口：同 ID 只探一次（computeIfAbsent 命中缓存）。
     *
     * <p>包级可见供脱平台单测注入计数探测函数；入口同样防御 probe 异常（按不可用处理）。
     *
     * @param language 语言实例
     * @param probe    探测函数（生产入口传 {@link #probeCapability}，测试可注入计数/异常探测）
     * @return 能力值（探测返回 null 或抛异常时归一化为 PLAIN_TEXT_FALLBACK；PCE 取消信号原样上抛且不缓存）
     */
    @Nullable
    static Capability capabilityFor(@NotNull Language language,
                                    @NotNull Function<Language, Capability> probe) {
        String id = language.getID();
        if (id == null) {
            // getID() 契约为 @NotNull，防御性兜底：不缓存，直接探测一次
            return safeProbe(language, probe);
        }
        try {
            return CACHE.computeIfAbsent(id, key -> safeProbe(language, probe));
        } catch (ProcessCanceledException e) {
            // 平台取消信号必须原样抛出，不可吞并（computeIfAbsent 内部异常本就不建立缓存条目）
            throw e;
        } catch (Exception e) {
            // computeIfAbsent 内部异常不建立缓存条目；此处再防御一层，按不可用处理
            return Capability.PLAIN_TEXT_FALLBACK;
        }
    }

    /**
     * 清空缓存。<b>仅供测试 / 运行期重置使用</b>（生产正常流程不应调用）。
     */
    public static void clear() {
        CACHE.clear();
    }

    /** 默认探测：token 层与 Commenter 层各探一次后组合。 */
    @NotNull
    static Capability probeCapability(@NotNull Language language) {
        return combine(probeTokenAvailable(language), probeCommenterAvailable(language));
    }

    /**
     * token 层探测：语言是否注册了"值得尝试"的 SyntaxHighlighter。
     * 异常（扩展点不可用 / 工厂对 null project 不健壮等）按不可用处理。
     */
    static boolean probeTokenAvailable(@NotNull Language language) {
        try {
            SyntaxHighlighter highlighter =
                    SyntaxHighlighterFactory.getSyntaxHighlighter(language, null, null);
            // 占位 PlainSyntaxHighlighter 无语义 key，视为不可用（语义偏差说明见类 javadoc）
            return highlighter != null && !(highlighter instanceof PlainSyntaxHighlighter);
        } catch (ProcessCanceledException e) {
            // 平台取消信号必须原样抛出，不可吞并
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Commenter 层探测：语言是否注册了 Commenter（{@code LanguageCommenters.forLanguage} 可为 null）。
     * 异常按不可用处理。
     */
    static boolean probeCommenterAvailable(@NotNull Language language) {
        try {
            return LanguageCommenters.INSTANCE.forLanguage(language) != null;
        } catch (ProcessCanceledException e) {
            // 平台取消信号必须原样抛出，不可吞并
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 纯逻辑：探测结果组合为能力值（可脱平台单测）。
     * 并存（token+commenter）优先表达 TOKEN_ONLY（Commenter 在 token 未命中后仍照常尝试）。
     * PSI_PRECISE 为保留值，不由探测产生（见类 javadoc）。
     */
    @NotNull
    static Capability combine(boolean tokenAvailable, boolean commenterAvailable) {
        if (tokenAvailable) {
            return Capability.TOKEN_ONLY;
        }
        if (commenterAvailable) {
            return Capability.COMMENTER_ONLY;
        }
        return Capability.PLAIN_TEXT_FALLBACK;
    }

    /** 单次探测归一化：异常 / null 结果一律归为 PLAIN_TEXT_FALLBACK；PCE（取消信号）除外——原样上抛且不缓存。 */
    @NotNull
    private static Capability safeProbe(@NotNull Language language,
                                        @NotNull Function<Language, Capability> probe) {
        try {
            Capability capability = probe.apply(language);
            return capability == null ? Capability.PLAIN_TEXT_FALLBACK : capability;
        } catch (ProcessCanceledException e) {
            // 平台取消信号必须原样抛出：不吞并，也不建立缓存条目
            throw e;
        } catch (Exception e) {
            return Capability.PLAIN_TEXT_FALLBACK;
        }
    }
}
