# CharAutoReplace 技术文档

## 1. 项目概述

**项目名称**: CharAutoReplace  
**项目类型**: IntelliJ IDEA 插件  
**主要功能**: 实时中英文混输标点替换助手——输入中文标点时自动替换为对应英文标点，支持区域策略（代码区恒开、注释/字符串自适应、控制台恒关）  
**支持版本**: IntelliJ IDEA 2020.3+（since-build 203，无 until-build 上限）  
**当前版本**: 1.8.0-beta1（2.0.0 为规划目标，变更内容见 CHANGELOG）

## 2. 技术架构

### 2.1 技术栈

| 组件 | 技术选型 |
|------|----------|
| 开发语言 | Java 11 |
| 构建工具 | Gradle 6.8+ |
| IDE框架 | IntelliJ Platform SDK |
| 依赖库 | 无第三方运行时依赖（HTML 转义纯 JDK 实现） |
| 测试框架 | JUnit Jupiter 5.7.2 + junit-vintage-engine |

### 2.2 项目结构

```
CharAutoReplace/
├── src/main/java/com/haojiyou/cnchar/
│   ├── common/
│   │   └── StrUtil.java                     # 字符串工具（纯 JDK，无第三方依赖）
│   │       （注：SupportFileType 旧枚举已删除——PLAIN_TEXT 扩展名判定由 core/RegionClassifier 的 PLAIN_TEXT_EXTENSIONS 承担：txt/md/gitignore/text/log）
│   ├── convert/
│   │   └── CharConverter.java               # 三层转换引擎（自定义 > 精选表 > 全角偏移）
│   ├── core/
│   │   ├── CjkContextDetector.java           # CJK 语境启发式检测（自适应策略）
│   │   ├── CommentContextResolver.java       # 上下文判定（PSI 主判 → token 兜底 → Commenter 兜底）
│   │   ├── EditorContext.java                # 不可变编辑器上下文值对象
│   │   ├── PolicyEngine.java                 # 区域策略引擎（ALWAYS/NEVER/ADAPTIVE）
│   │   ├── RegionCapabilityCache.java        # 语言兜底能力缓存（token/Commenter 可用性，按 language.getID() 惰性探测）
│   │   ├── RegionClassifier.java             # 区域分类器（UNKNOWN 兜底改 PLAIN_TEXT 自适应）
│   │   └── ReplacementExecutor.java          # 同步替换 + 光标修正 + 单步撤销 + 提示
│   ├── handler/
│   │   └── ChineseCharCheckHandler.java      # charTyped 管线入口（PSI 提交前置 + 文本兜底）
│   ├── region/
│   │   └── InputRegion.java                  # 区域枚举
│   ├── rule/
│   │   ├── BlankInputRule.java               # 前置规则：空白输入过滤（候选空白字符放行，如全角空格 U+3000 可穿过后被转换为半角空格）
│   │   ├── InputLengthRule.java              # 前置规则：输入长度过滤
│   │   ├── NoMappingRule.java                # 前置规则：无映射过滤
│   │   ├── ReplacementRule.java              # 规则接口
│   │   └── ReplacementRuleChain.java         # 规则链（组合模式）
│   ├── service/
│   │   ├── CharAutoReplaceHelpProvider.java  # webHelpProvider：帮助主题 → 在线文档 URL
│   │   └── HintService.java                  # 替换提示显示服务（公共 HintManager API）
│   └── settings/
│       ├── CharAutoReplaceConfigurable.java   # 设置 UI（FormBuilder + JBTable + 每区三态）
│       ├── CharAutoReplaceSettings.java       # PersistentStateComponent + 不可变快照
│       ├── LegacyConfigMigrator.java          # 旧配置迁移器
│       ├── MappingRule.java                   # 自定义映射规则值对象
│       └── RegionPolicy.java                  # 区域策略枚举与默认值
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml                        # 插件配置
├── src/test/java/                             # 单元测试与集成测试
├── build.gradle                              # Gradle 构建配置
└── docs/
    ├── TECHNICAL.md                          # 本文档
    └── CHANGELOG.md                          # 版本变更日志
```

## 3. 核心架构

### 3.1 charTyped 管线

```
用户输入字符
     ↓
ChineseCharCheckHandler.charTyped()
     ↓
isCandidateChar() — O(1) 位图过滤
     ↓
PsiDocumentManager.commitDocument() — PSI 前置提交（失败告警后落回文本兜底）
     ↓
EditorContext 构建（不可变值对象）
     ↓
ReplacementRuleChain.checkAll() — 前置规则链
     ↓
RegionClassifier.classify() — 区域识别
     ↓
PolicyEngine.decide() — 策略判定（ALWAYS/NEVER/ADAPTIVE）
     ↓
CharConverter.convert() — 三层转换
     ↓
ReplacementExecutor.execute() — WriteCommandAction 替换 + 单步撤销
```

### 3.2 区域策略

| 区域 | 默认策略 | 说明 |
|------|----------|------|
| CODE | ALWAYS | 代码区恒开 |
| COMMENT | ADAPTIVE | 按光标前文中英文语境启发式决定 |
| STRING | ADAPTIVE | 同上 |
| COMMIT | ADAPTIVE | Git 提交信息 |
| CONSOLE | NEVER | 控制台恒关 |
| PLAIN_TEXT | ADAPTIVE | 纯文本 |
| UNREACHABLE | NEVER | 不可达区域，恒排除 |

### 3.3 上下文判定兜底阶梯（PSI → token → Commenter）

COMMENT/STRING/CODE 由 `CommentContextResolver` 按三级阶梯判定，降级路径由 `RegionCapabilityCache` 缓存决策：

1. **PSI 主判**（恒先行、逐次判定，不缓存）：`PsiComment` → 注释；`PsiLanguageInjectionHost` → 字符串/注入。
2. **SyntaxHighlighter token 兜底**：取编辑器高亮器（`EditorEx.getHighlighter()` 优先，备选 `HighlighterFactory.createHighlighter`）光标处 token，经 `SyntaxHighlighterFactory` 的 `getTokenHighlights(tokenType)` 映射：命中 `DefaultLanguageHighlighterColors` 的 LINE_COMMENT/BLOCK_COMMENT/DOC_COMMENT → COMMENT、STRING → STRING。仅命中平台默认语义 key，语言自定义 key（如 JAVA_STRING）不命中（已知局限，须 runIde 实测命中广度）。
3. **Commenter 兜底**：行注释前缀判定 / 未闭合块注释扫描。

`RegionCapabilityCache` 为 application 级缓存（key = `language.getID()`，首次遇到惰性探测并缓存），避免每键重复扩展点查询：

| 能力 | 降级行为 |
|------|----------|
| PSI_PRECISE | 保留值（当前探测不产生）：语义为不进入兜底路径 → 短路 UNKNOWN |
| TOKEN_ONLY | token 层可用（Commenter 并存语义）：先 token 层，未命中仍走 Commenter |
| COMMENTER_ONLY | 仅 Commenter 可用：跳过 token 层 |
| PLAIN_TEXT_FALLBACK | 两者皆不可用：兜底路径整体短路 UNKNOWN |

**UNKNOWN 区域兜底**：resolver 返回 UNKNOWN（无任何可用判据，如 offset 越界）时，区域归类由旧版 CODE（默认 ALWAYS 恒替换）改为 **PLAIN_TEXT 自适应**（CJK 语境决策，降低误替换风险）；`psiFile == null` 仍为 UNREACHABLE。

### 3.4 三层转换引擎

1. **自定义映射**（用户配置，最高优先级）
2. **精选 CJK 标点表**（内置 curated table）
3. **全角偏移算术**（0xFF01–0xFF5E 范围，O(1) 计算）

### 3.5 配置模型

- `CharAutoReplaceSettings` 实现 `PersistentStateComponent`，持久化用户配置
- `Snapshot` 不可变快照：volatile 字段 + 原子引用，读取零锁
- 位图候选探测：`boolean[65536]`，O(1) 判断字符是否可能需要替换
- `LegacyConfigMigrator` 兼容旧版配置格式自动迁移

### 3.6 设置 UI

- `CharAutoReplaceConfigurable`：FormBuilder 链式布局 + JBTable 自定义映射（无行数上限）
- 每区域三态 ComboBox（开/关/自适应）
- 恢复默认：平台标准 ActionLink
- 替换提示开关
- 帮助主题 `CharAutoReplace.settings`（F1 经 webHelpProvider 跳转在线文档）

## 4. 插件配置

### 4.1 plugin.xml 关键扩展点

```xml
<extensions defaultExtensionNs="com.intellij">
    <applicationConfigurable parentId="tools" 
        instance="com.haojiyou.cnchar.settings.CharAutoReplaceConfigurable"/>
    <typedHandler implementation="com.haojiyou.cnchar.handler.ChineseCharCheckHandler"/>
    <applicationService serviceImplementation="com.haojiyou.cnchar.service.HintService"/>
    <applicationService serviceImplementation="com.haojiyou.cnchar.settings.CharAutoReplaceSettings"/>
    <webHelpProvider implementation="com.haojiyou.cnchar.service.CharAutoReplaceHelpProvider"/>
</extensions>
```

其中 `webHelpProvider` 以 `helpTopicPrefix="CharAutoReplace."` 匹配设置页帮助主题（`CharAutoReplace.settings`），帮助页指向 GitHub 仓库（见第 7 节）。

## 5. 性能特征

- **候选位图**：`boolean[65536]`，O(1) 零分配探测
- **不可变快照**：配置变更时构建新快照，读取路径无锁
- **规则链短路**：前置规则任一失败即返回，不进入区域分类
- **能力缓存**：语言兜底能力按 `language.getID()` 惰性探测一次并缓存，兜底路径按能力降级短路
- **WriteCommandAction**：单步撤销，替换操作可一次性撤回

## 6. 兼容性

- 最低支持: IDEA 203.5981 (2020.3)
- 依赖模块: `com.intellij.modules.platform`, `com.intellij.modules.lang`
- 兼容 CLion、Rider 等基于 IntelliJ 平台的 IDE

## 7. 外部资源

- GitHub: [https://github.com/ranbest/ChineseCharReplace](https://github.com/ranbest/ChineseCharReplace)
- Gitee: [https://gitee.com/bestxu/chinese-char-replace](https://gitee.com/bestxu/chinese-char-replace)
- JetBrains Plugin Marketplace: [17345-charautoreplace](https://plugins.jetbrains.com/plugin/17345-charautoreplace)

## 8. runIde 人工实测验收清单

以下行为无法在纯单元/fixture 测试中覆盖，发布前须在 runIde 中人工验收：

- **UNKNOWN→PLAIN_TEXT 端到端归类**：在真实编辑器中构造无法判定上下文的场景（如光标偏移越界、无任何可用判据），确认其走纯文本区自适应策略（默认按光标前文中英文语境决策），而非旧的代码区恒替换；无 PSI 体系的场景仍应判为 UNREACHABLE 恒排除。
- **替换提示出现/消失时机**：提示已迁移平台公共 API，定位与隐藏 flags 为平台默认值（原精确偏移 (12,0) 与 hide flags=12 不再保留）；重点观察连续打字、滚动、任意按键时提示的出现与隐藏感知是否可接受，如出现可感知偏差再评估处理方案（勿直接改回内部 API）。
