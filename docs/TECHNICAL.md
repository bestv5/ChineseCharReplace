# CharAutoReplace 技术文档

## 1. 项目概述

**项目名称**: CharAutoReplace  
**项目类型**: IntelliJ IDEA 插件  
**主要功能**: 实时中英文混输标点替换助手——输入中文标点时自动替换为对应英文标点，支持区域策略（代码区恒开、注释/字符串自适应、控制台恒关）  
**支持版本**: IntelliJ IDEA 2020.3+（since-build 203，无 until-build 上限）  
**当前版本**: 2.0.0

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
│   │   ├── MyConst.java                     # 常量定义
│   │   ├── StrUtil.java                     # 字符串工具（纯 JDK，无第三方依赖）
│   │   └── SupportFileType.java             # 支持的文件类型枚举
│   ├── convert/
│   │   └── CharConverter.java               # 三层转换引擎（自定义 > 精选表 > 全角偏移）
│   ├── core/
│   │   ├── CjkContextDetector.java           # CJK 语境启发式检测（自适应策略）
│   │   ├── CommentContextResolver.java       # PSI + Commenter 兜底注释识别
│   │   ├── EditorContext.java                # 不可变编辑器上下文值对象
│   │   ├── PolicyEngine.java                 # 区域策略引擎（ALWAYS/NEVER/ADAPTIVE）
│   │   ├── RegionClassifier.java             # 区域分类器（CODE/COMMENT/STRING/COMMIT/CONSOLE/PLAIN_TEXT）
│   │   └── ReplacementExecutor.java          # 同步替换 + 光标修正 + 单步撤销 + 提示
│   ├── handler/
│   │   └── ChineseCharCheckHandler.java      # charTyped 管线入口
│   ├── region/
│   │   └── InputRegion.java                  # 区域枚举
│   ├── rule/
│   │   ├── BlankInputRule.java               # 前置规则：空输入过滤
│   │   ├── InputLengthRule.java              # 前置规则：输入长度过滤
│   │   ├── NoMappingRule.java                # 前置规则：无映射过滤
│   │   ├── ReplacementRule.java              # 规则接口
│   │   └── ReplacementRuleChain.java         # 规则链（组合模式）
│   ├── service/
│   │   └── HintService.java                  # 替换提示显示服务
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

### 3.3 三层转换引擎

1. **自定义映射**（用户配置，最高优先级）
2. **精选 CJK 标点表**（内置 curated table）
3. **全角偏移算术**（0xFF01–0xFF5E 范围，O(1) 计算）

### 3.4 配置模型

- `CharAutoReplaceSettings` 实现 `PersistentStateComponent`，持久化用户配置
- `Snapshot` 不可变快照：volatile 字段 + 原子引用，读取零锁
- 位图候选探测：`boolean[65536]`，O(1) 判断字符是否可能需要替换
- `LegacyConfigMigrator` 兼容旧版配置格式自动迁移

### 3.5 设置 UI

- `CharAutoReplaceConfigurable`：FormBuilder 布局 + JBTable 自定义映射（无行数上限）
- 每区域三态 ComboBox（开/关/自适应）
- 恢复默认按钮
- 替换提示开关

## 4. 插件配置

### 4.1 plugin.xml 关键扩展点

```xml
<extensions defaultExtensionNs="com.intellij">
    <applicationConfigurable parentId="tools" 
        instance="com.haojiyou.cnchar.settings.CharAutoReplaceConfigurable"/>
    <typedHandler implementation="com.haojiyou.cnchar.handler.ChineseCharCheckHandler"/>
    <applicationService serviceImplementation="com.haojiyou.cnchar.service.HintService"/>
    <applicationService serviceImplementation="com.haojiyou.cnchar.settings.CharAutoReplaceSettings"/>
</extensions>
```

## 5. 性能特征

- **候选位图**：`boolean[65536]`，O(1) 零分配探测
- **不可变快照**：配置变更时构建新快照，读取路径无锁
- **规则链短路**：前置规则任一失败即返回，不进入区域分类
- **WriteCommandAction**：单步撤销，替换操作可一次性撤回

## 6. 兼容性

- 最低支持: IDEA 203.5981 (2020.3)
- 依赖模块: `com.intellij.modules.platform`, `com.intellij.modules.lang`
- 兼容 CLion、Rider 等基于 IntelliJ 平台的 IDE

## 7. 外部资源

- GitHub: [https://github.com/ranbest/ChineseCharReplace](https://github.com/ranbest/ChineseCharReplace)
- Gitee: [https://gitee.com/bestxu/chinese-char-replace](https://gitee.com/bestxu/chinese-char-replace)
- JetBrains Plugin Marketplace: [17345-charautoreplace](https://plugins.jetbrains.com/plugin/17345-charautoreplace)
