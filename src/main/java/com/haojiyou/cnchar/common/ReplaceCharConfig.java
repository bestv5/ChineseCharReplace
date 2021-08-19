package com.haojiyou.cnchar.common;

import com.haojiyou.cnchar.CnCharSettingComponent;
import com.intellij.ide.util.PropertiesComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述: 配置
 *
 * @author : best.xu
 */
public class ReplaceCharConfig {
    /**
     * 缓存替换中英文字符配置
     */
    public static Map<String, String> cnCharMap = new HashMap<>();
    /**
     * 是否显示替换提示
     */
    public static boolean showRepacedMsg = false;
    /**
     * 注释区域是否替换
     */
    public static boolean replaceInComment = false;

    static {
        reload();
    }

    public static void reload() {
        cnCharMap.clear();
        String[] configString = PropertiesComponent.getInstance().getValue(CnCharSettingComponent.KEY, CnCharSettingComponent.DEFAULT_STRING).split("\n");
        for (int i = 0; i < configString.length / 2; i++) {
            cnCharMap.put(configString[2 * i].trim(), configString[2 * i + 1].trim());
        }
        showRepacedMsg = PropertiesComponent.getInstance().getBoolean(CnCharSettingComponent.REPLACED_MSG_SHOW_KEY, false);

        replaceInComment = PropertiesComponent.getInstance().getBoolean(CnCharSettingComponent.REPLACED_IN_COMMENT_KEY, false);
    }
}
