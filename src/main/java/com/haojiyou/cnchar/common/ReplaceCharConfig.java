package com.haojiyou.cnchar.common;

import com.haojiyou.cnchar.region.InputRegion;
import com.haojiyou.cnchar.settings.CharAutoReplaceSettings;
import com.haojiyou.cnchar.settings.RegionPolicy;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述: 配置。
 *
 * <p><b>⚠ 过渡 shim —— 阶段5 删除。</b>
 * 本类已由“解析旧 PropertiesComponent KEY”改为<b>薄委托</b>：{@link #reload()} 从
 * {@link CharAutoReplaceSettings#getInstance()} 的<b>不可变快照</b>读取，填充下列既有 public 静态字段，
 * 使旧消费者（{@code CharTypedDocumentLisener} / {@code CharAutoReplaceAction} / {@code CnCharSettingComponent}）
 * <b>继续编译运行且与新模型同源</b>——即便迁移已清除旧 KEY，旧路径仍可用。
 *
 * <p><b>字段与方法签名保持不变</b>，以免破坏旧调用点。新代码请直接使用 {@link CharAutoReplaceSettings}。
 *
 * @author : best.xu
 */
public class ReplaceCharConfig {
    /**
     * 缓存替换中英文字符配置（过渡期由新快照的合并映射表填充）。
     */
    public static Map<String, String> cnCharMap = new HashMap<>();
    /**
     * 是否显示替换提示。
     */
    public static boolean showRepacedMsg = false;
    /**
     * 注释区域是否替换。
     */
    public static boolean replaceInComment = false;

    static {
        reload();
    }

    public static void reload() {
        CharAutoReplaceSettings settings = CharAutoReplaceSettings.getInstance();
        CharAutoReplaceSettings.Snapshot snapshot = settings.getSnapshot();

        // 与新模型同源：快照的合并映射表（层1 自定义 + 层2 精选 CJK，含 、→, 修正）。
        Map<String, String> fresh = new HashMap<>(snapshot.getCharMap());
        // 旧消费者是纯表驱动（cnCharMap.get(fragment)），不会走转换器的全角偏移算术；
        // 为保持过渡期旧路径行为等价，补齐全角区 0xFF01–0xFF5E 与表意空格 U+3000 的映射。
        // putIfAbsent 确保用户自定义 / 精选表条目优先。
        for (int c = 0xFF01; c <= 0xFF5E; c++) {
            fresh.putIfAbsent(String.valueOf((char) c), String.valueOf((char) (c - 0xFEE0)));
        }
        fresh.putIfAbsent(String.valueOf((char) 0x3000), " ");

        // 单次引用赋值（原子），避免 clear()+putAll() 期间被旧消费者读到半填状态。
        cnCharMap = fresh;

        showRepacedMsg = settings.getState().showHint;
        // 旧布尔语义：true=注释内替换。映射到新三态：仅 ALWAYS 视为 true；NEVER/ADAPTIVE 视为 false。
        replaceInComment = snapshot.getMode(InputRegion.COMMENT) == RegionPolicy.ReplaceMode.ALWAYS;
    }
}
