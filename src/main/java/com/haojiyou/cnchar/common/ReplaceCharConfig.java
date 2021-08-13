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
    public static boolean showRepacedMsg = false;

    static {
        reload();
    }

    public static void reload() {
        cnCharMap.clear();
        String[] configString = PropertiesComponent.getInstance().getValue(CnCharSettingComponent.KEY, CnCharSettingComponent.DEFAULT_STRING).split("\n");
        for (int i = 0; i < configString.length / 2; i++) {
            cnCharMap.put(configString[2 * i].trim(), configString[2 * i + 1].trim());
        }
        showRepacedMsg = Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(CnCharSettingComponent.REPLACED_MSG_SHOW_KEY, "false"));
    }
}
