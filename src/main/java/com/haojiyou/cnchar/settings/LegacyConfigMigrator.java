package com.haojiyou.cnchar.settings;

import com.haojiyou.cnchar.common.StrUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述: 旧配置（3 个 {@link PropertiesComponent} KEY + “换行成对串”格式）到新模型 {@link CharAutoReplaceSettings.State}
 * 的一次性迁移。
 *
 * <p><b>纯迁移逻辑与 PropertiesComponent I/O 分离</b>：
 * <ul>
 *   <li>{@link #migrate(String, String, String)} 为<b>纯函数</b>（可脱平台单测）；</li>
 *   <li>{@link #ensureMigrated(CharAutoReplaceSettings)} 为薄封装，读写 {@link PropertiesComponent}。</li>
 * </ul>
 *
 * <p><b>customMappings 决策</b>：若旧串缺省或<b>恰等于旧 {@code DEFAULT_STRING}</b>（用户从未自定义）→ 采用
 * <b>新默认</b>（层2 精选表，含 {@code 、→,} 修正），{@code customMappings} 留空；否则忠实解析成对串为
 * {@code customMappings}（保留用户原值，即便含 {@code 、→/}）。解析算法与 {@code ReplaceCharConfig.reload()} /
 * 阶段0 {@code LegacyConfigParseTest} 一致（{@code length/2} 对、trim）。
 *
 * <p><b>数据安全</b>：仅当迁移成功写入新 State 后，才 {@code migrated=true} 并<b>清除旧 KEY</b>；任何异常/失败 →
 * <b>不清旧 KEY</b>、保持 {@code migrated=false}（下次初始化重试），确保老用户设置不丢。
 *
 * @author : best.xu
 */
public final class LegacyConfigMigrator {

    private static final Logger LOG = Logger.getInstance(LegacyConfigMigrator.class);

    /** 旧 KEY：换行成对映射串（与 {@code CnCharSettingComponent.KEY:20} 一致）。 */
    public static final String LEGACY_KEY_CONFIG = "cnchar_config_string";
    /** 旧 KEY：是否显示替换提示（与 {@code CnCharSettingComponent.REPLACED_MSG_SHOW_KEY:21} 一致）。 */
    public static final String LEGACY_KEY_MSG_SHOW = "cnchar_config_replace_msg_hit";
    /** 旧 KEY：注释内是否替换（与 {@code CnCharSettingComponent.REPLACED_IN_COMMENT_KEY:22} 一致）。 */
    public static final String LEGACY_KEY_REPLACE_IN_COMMENT = "cnchar_config_replace_in_comment";

    /**
     * 旧默认映射串，与 {@code CnCharSettingComponent.DEFAULT_STRING:18} <b>逐字符一致</b>
     * （空格分隔成对串，随后 {@code " " → "\n"}）。此处自持常量，便于阶段5 删除旧 UI 类后仍可迁移。
     * 用 Unicode 转义书写以规避源码文件编码差异。
     * 顺序：， 。 ： ； ！ ？ “ ” ‘ ’ 【 】 （ ） 「 」 《 》 、
     */
    static final String LEGACY_DEFAULT_STRING =
            ("\uFF0C , \u3002 . \uFF1A : \uFF1B ; \uFF01 ! \uFF1F ? "
                    + "\u201C \" \u201D \" \u2018 ' \u2019 ' "
                    + "\u3010 [ \u3011 ] \uFF08 ( \uFF09 ) "
                    + "\u300C { \u300D } \u300A < \u300B > \u3001 /")
                    .replace(" ", "\n");

    private LegacyConfigMigrator() {
    }

    /**
     * 纯迁移函数（无平台依赖，可脱 Application 单测）。
     *
     * @param configStr            旧映射串（缺省传 null）
     * @param msgShowRaw           旧“显示提示”原始串（缺省传 null）
     * @param replaceInCommentRaw  旧“注释内替换”原始串（缺省传 null）
     * @return 迁移后的新 State（{@code migrated} 由外层封装负责置位）
     */
    public static CharAutoReplaceSettings.State migrate(String configStr,
                                                        String msgShowRaw,
                                                        String replaceInCommentRaw) {
        CharAutoReplaceSettings.State state = new CharAutoReplaceSettings.State();

        // --- customMappings：缺省或恰等于旧默认串 → 采用新默认（精选表，含 、→,），自定义表留空 ---
        if (StrUtil.isBlank(configStr) || LEGACY_DEFAULT_STRING.equals(configStr)) {
            state.customMappings = new ArrayList<>();
        } else {
            state.customMappings = parsePairs(configStr);
        }

        // --- showHint ← msgShowRaw（缺省 → false） ---
        state.showHint = parseBooleanOrFalse(msgShowRaw);

        // --- COMMENT 区模式 ← replaceInCommentRaw ---
        if (StrUtil.isBlank(replaceInCommentRaw)) {
            // 键缺省（从未设）→ 用新默认 ADAPTIVE
            state.commentMode = RegionPolicy.ReplaceMode.ADAPTIVE;
        } else if (Boolean.parseBoolean(replaceInCommentRaw.trim())) {
            // 显式 true → ALWAYS（忠实保留“注释内也替换”）
            state.commentMode = RegionPolicy.ReplaceMode.ALWAYS;
        } else {
            // 显式 false → NEVER（忠实保留“注释内不替换”的意图）
            state.commentMode = RegionPolicy.ReplaceMode.NEVER;
        }
        return state;
    }

    /**
     * 复刻 {@code ReplaceCharConfig.reload()} 的“换行成对串 → 映射”解析算法（{@code length/2} 对、trim）。
     */
    static List<MappingRule> parsePairs(String value) {
        List<MappingRule> list = new ArrayList<>();
        String[] arr = value.split("\n");
        for (int i = 0; i < arr.length / 2; i++) {
            String from = arr[2 * i].trim();
            String to = arr[2 * i + 1].trim();
            list.add(new MappingRule(from, to));
        }
        return list;
    }

    private static boolean parseBooleanOrFalse(String raw) {
        return raw != null && Boolean.parseBoolean(raw.trim());
    }

    /**
     * 薄封装：读旧 3 KEY → 纯迁移 → 写入新 State；<b>仅成功后</b>置 {@code migrated=true} 并清除旧 KEY。
     * 任何异常 → 不清旧 KEY、返回 false（下次重试）。
     *
     * @return {@code true} 表示已迁移（或此前已迁移），无需重试；{@code false} 表示本次失败，应下次重试
     */
    public static boolean ensureMigrated(CharAutoReplaceSettings settings) {
        CharAutoReplaceSettings.State current = settings.getState();
        if (current != null && current.migrated) {
            return true;
        }
        try {
            PropertiesComponent pc = PropertiesComponent.getInstance();
            String configStr = pc.getValue(LEGACY_KEY_CONFIG);
            String msgShowRaw = pc.getValue(LEGACY_KEY_MSG_SHOW);
            String replaceInCommentRaw = pc.getValue(LEGACY_KEY_REPLACE_IN_COMMENT);

            CharAutoReplaceSettings.State migrated = migrate(configStr, msgShowRaw, replaceInCommentRaw);
            migrated.migrated = true;
            // 先写入新 State（构建并交换快照）成功后，再清除旧 KEY
            settings.loadState(migrated);
            pc.unsetValue(LEGACY_KEY_CONFIG);
            pc.unsetValue(LEGACY_KEY_MSG_SHOW);
            pc.unsetValue(LEGACY_KEY_REPLACE_IN_COMMENT);
            return true;
        } catch (RuntimeException e) {
            // 失败：不清旧 KEY、保持 migrated=false，下次初始化重试，确保老用户设置不丢
            LOG.warn("CharAutoReplace 旧配置迁移失败，保留旧 KEY 以便下次重试", e);
            return false;
        }
    }
}
