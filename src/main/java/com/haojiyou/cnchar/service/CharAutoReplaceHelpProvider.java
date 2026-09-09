package com.haojiyou.cnchar.service;

import com.intellij.openapi.help.WebHelpProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 描述: CharAutoReplace 帮助主题 → 在线文档 URL 映射。
 *
 * <p>经 plugin.xml 的 {@code <webHelpProvider>} 扩展点注册：设置页
 * {@link com.haojiyou.cnchar.settings.CharAutoReplaceConfigurable#getHelpTopic()}
 * 返回 {@code "CharAutoReplace.settings"}，平台按前缀
 * {@link #getHelpTopicPrefix()} 匹配到本 Provider 后，调用
 * {@link #getHelpPageUrl(String)} 取得文档地址（设置页 F1 帮助跳转）。
 *
 * <p>注：IC-2020.3 中 {@code WebHelpProvider} 是 public abstract class（非接口，
 * javap 已核实），故用 extends 继承；getHelpPageUrl(String) 是唯一抽象方法，
 * getHelpTopicPrefix() 为带默认实现的非抽象方法，覆盖均合法。
 *
 * @author : lixuran
 */
public class CharAutoReplaceHelpProvider extends WebHelpProvider {

    /** 本插件帮助主题统一前缀（保留末尾 '.'，避免误匹配其它以 CharAutoReplace 开头的无关主题）。 */
    static final String HELP_TOPIC_PREFIX = "CharAutoReplace.";

    /** 项目文档主页：README.md 确认的 GitHub 仓库地址（与 plugin.xml vendor url 一致）。 */
    private static final String DOCUMENTATION_URL = "https://github.com/ranbest/ChineseCharReplace";

    @NotNull
    @Override
    public String getHelpTopicPrefix() {
        return HELP_TOPIC_PREFIX;
    }

    @Nullable
    @Override
    public String getHelpPageUrl(@NotNull String helpTopic) {
        // 平台 203 语义：传入完整 helpTopic（如 "CharAutoReplace.settings"）；
        // 兼容个别调用方传去除前缀后的 topicId（如 "settings"）。
        if (helpTopic.startsWith(HELP_TOPIC_PREFIX) || "settings".equals(helpTopic)) {
            return DOCUMENTATION_URL;
        }
        // 非本插件主题返回 null，交还平台默认处理，避免劫持其它主题。
        return null;
    }
}
