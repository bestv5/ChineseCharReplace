package com.haojiyou.cnchar.region;

/**
 * 描述: 输入发生所在的语义区域。纯逻辑枚举，不依赖任何 IDE / 平台类型，可脱离平台单测。
 *
 * <p>阶段2 仅定义枚举本身；区域判定（RegionClassifier）与策略决策（PolicyEngine）留待阶段3。
 * 每个区域统一使用三态策略 {@code RegionPolicy.ReplaceMode{ALWAYS/NEVER/ADAPTIVE}}（见 spec 区域策略表）。
 *
 * @author : best.xu
 */
public enum InputRegion {
    /** 代码区：默认 ALWAYS（全角标点必须转半角）。 */
    CODE,
    /** 注释区：默认 ADAPTIVE（按光标前文中英文语境决定）。 */
    COMMENT,
    /** 字符串区：默认 ADAPTIVE（英文串→替换、中文串→保留）。 */
    STRING,
    /** Git 提交框：默认 ADAPTIVE（提交信息是自然语言）。 */
    COMMIT,
    /** 控制台：默认 NEVER（默认不替换，可开启）。 */
    CONSOLE,
    /** 纯文本 / 自然语言（.txt/.md/.gitignore 等无代码 PSI 的编辑器）：默认 ADAPTIVE。 */
    PLAIN_TEXT,
    /** 不可达：Terminal、普通 Swing 文本框、对话框等 typed handler 触达不到的区域，恒排除。 */
    UNREACHABLE
}
