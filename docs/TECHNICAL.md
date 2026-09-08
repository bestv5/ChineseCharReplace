# CharAutoReplace 技术文档

## 1. 项目概述

**项目名称**: CharAutoReplace  
**项目类型**: IntelliJ IDEA 插件  
**主要功能**: 在编写代码时自动将中文标点符号替换为英文标点符号，提高编码效率并延长键盘使用寿命  
**支持版本**: IntelliJ IDEA 2020.3+  
**当前版本**: 1.7.0

## 2. 技术架构

### 2.1 技术栈

| 组件 | 技术选型 |
|------|----------|
| 开发语言 | Java 11 |
| 构建工具 | Gradle 6.8 |
| IDE框架 | IntelliJ Platform SDK |
| 依赖库 | Apache Commons Text 1.12.0 |
| 测试框架 | JUnit Jupiter 5.7.2 |

### 2.2 项目结构

```
CharAutoReplace/
├── src/main/java/com/haojiyou/cnchar/
│   ├── CharTypedDocumentLisener.java       # 文档监听器 - 核心替换逻辑
│   ├── CnCharSettingComponent.java         # 设置面板组件
│   ├── action/
│   │   └── CharAutoReplaceAction.java       # 字符替换执行动作
│   ├── common/
│   │   ├── CnCharCommentUtil.java           # 注释区域判断工具类
│   │   ├── DocumentUtil.java                 # 文档操作工具类
│   │   ├── MyConst.java                     # 常量定义
│   │   ├── ReplaceCharConfig.java           # 替换配置管理
│   │   └── SupportFileType.java             # 支持的文件类型枚举
│   ├── handler/
│   │   └── ChineseCharCheckHandler.java     # 字符输入处理器
│   └── service/
│       └── HintService.java                 # 提示信息显示服务
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml                       # 插件配置文件
├── build.gradle                             # Gradle 构建配置
└── docs/
    └── CHANGELOG.md                         # 版本变更日志
```

## 3. 核心模块详解

### 3.1 字符输入处理链

```
用户输入字符
     ↓
ChineseCharCheckHandler.beforeCharTyped()
     ↓
添加 DocumentListener
     ↓
CharTypedDocumentLisener.documentChanged()
     ↓
判断是否可替换
     ↓
CharAutoReplaceAction.replace()
     ↓
执行替换 + 显示提示(可选)
```

### 3.2 核心组件

#### 3.2.1 ChineseCharCheckHandler

**位置**: `handler/ChineseCharCheckHandler.java`  
**职责**: 字符输入前置处理器，实现 `TypedHandlerDelegate` 接口  
**关键方法**:

- `beforeCharTyped()`: 在字符输入前触发，添加文档监听器

```java
@Override
public @NotNull Result beforeCharTyped(char c, @NotNull Project project, 
                                       @NotNull Editor editor, 
                                       @NotNull PsiFile file, 
                                       @NotNull FileType fileType) {
    editor.getDocument().addDocumentListener(new CharTypedDocumentLisener(editor, file));
    return Result.CONTINUE;
}
```

#### 3.2.2 CharTypedDocumentLisener

**位置**: `CharTypedDocumentLisener.java`  
**职责**: 实现 `DocumentListener` 接口，监听文档变化并执行替换逻辑  
**核心逻辑**:

1. 过滤输入长度 > 5 的情况
2. 查找替换映射 (`ReplaceCharConfig.cnCharMap`)
3. 判断当前光标位置是否在注释区域
4. 判断是否满足替换条件

**关键方法**: `isCanBeReplaced()`

- 检查是否为空输入
- 检查是否存在替换映射
- 使用 PSI API 判断注释区域
- 处理自定义注释识别

#### 3.2.3 CharAutoReplaceAction

**位置**: `action/CharAutoReplaceAction.java`  
**职责**: 执行实际的字符替换操作  
**关键特性**:

- 线程安全: 使用 synchronized 关键字
- 异步执行: 使用 `ApplicationManager.getApplication().invokeLater()`
- 写入操作: 使用 `WriteCommandAction.runWriteCommandAction()`
- 提示功能: 替换后显示提示信息(可选)

```java
public synchronized void replace(@NotNull DocumentEvent event, Editor editor,
                                  String originalText, String replacement) {
    ApplicationManager.getApplication().invokeLater(() -> {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.replaceString(event.getOffset(), currentOffset, replacement);
            // 显示替换提示(如果启用)
        });
    });
}
```

### 3.3 注释区域识别

#### 3.3.1 CnCharCommentUtil

**位置**: `common/CnCharCommentUtil.java`  
**支持的文件类型**:

| 文件类型 | 注释开始符 | 注释结束符 |
|----------|------------|------------|
| Java | `//`, `/*` | `*/` |
| JavaScript | `//`, `/*` | `*/` |
| TypeScript | `//`, `/*` | `*/` |
| TSX | `//`, `/*` | `*/` |
| C/C++ | `//`, `/*` | `*/` |
| SQL | `--`, `/*` | `*/` |
| XML | `<!--` | `-->` |
| Git Ignore | `#` | - |

> C/C++ 支持的扩展名：`.cpp`、`.cc`、`.cxx`、`.c++`、`.c`、`.h`、`.hpp`、`.hh`、`.hxx`、`.h++`、`.tpp`、`.inl`、`.ipp`。以上类型在 `SupportFileType` 枚举与 `CnCharCommentUtil` 的注释标记映射中成对注册，适用于 CLion/Rider 等 IDE 编辑 C/C++ 文件的场景。

**核心方法**:

- `isComment()`: 判断当前行是否是注释
- `isCustomComment()`: 判断是否为自定义注释区域
- `isAfterEndOfComment()`: 判断是否在块注释结束符之后

### 3.4 配置管理

#### 3.4.1 ReplaceCharConfig

**位置**: `common/ReplaceCharConfig.java`  
**配置项**:

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `cnCharMap` | Map<String, String> | 中文→英文标点映射 | 替换映射表 |
| `showRepacedMsg` | boolean | false | 是否显示替换提示 |
| `replaceInComment` | boolean | false | 是否在注释区域替换 |

**默认替换映射**:

```
， → ,   。 → .   ： → :   ； → ;   ！ → !   ？ → ?   
" → "   " → "   ' → '   ' → '   【 → [   】 → ]   
（ → (   ） → )   「 → {   」 }   《 → <   》 >   、 → /
```

#### 3.4.2 CnCharSettingComponent

**位置**: `CnCharSettingComponent.java`  
**职责**: IntelliJ IDEA 设置界面配置组件  
**功能**:

- 30组中英文标点映射配置
- 启用替换提示复选框
- 启用注释内替换复选框
- 恢复默认设置按钮

### 3.5 提示服务

#### 3.5.1 HintService

**位置**: `service/HintService.java`  
**功能**: 在编辑器中显示替换提示信息  
**提示样式**: HTML 格式的浮动提示

## 4. 插件配置

### 4.1 plugin.xml 配置

```xml
<idea-plugin>
    <id>com.haojiyou.CharAutoReplace</id>
    <name>CharAutoReplace</name>
    <vendor email="lixr873@163.com" url="https://github.com/ranbest/ChineseCharReplace">best.xu</vendor>
    <description>
        中文字符自动替换成英文字符插件。提高了写代码效率，增加了键盘寿命。
    </description>
    <idea-version since-build="203.5981"/>
    
    <extensions defaultExtensionNs="com.intellij">
        <!-- 设置面板 -->
        <applicationConfigurable parentId="tools" 
            instance="com.haojiyou.cnchar.CnCharSettingComponent"/>
        
        <!-- 字符输入处理器 -->
        <typedHandler implementation="com.haojiyou.cnchar.handler.ChineseCharCheckHandler"/>
        
        <!-- 提示服务 -->
        <applicationService serviceImplementation="com.haojiyou.cnchar.service.HintService"/>
    </extensions>
</idea-plugin>
```

## 5. 版本历史

| 版本 | 更新内容 |
|------|----------|
| 1.7.0 | 增加 .cpp, .h, .ts, .tsx 扩展名支持 |
| 1.6.1 | 移除过时的 API |
| 1.6.0 | 添加注释区域替换配置选项 |
| 1.5.0 | 添加替换提示显示配置 |
| 1.4.0 | 添加注释行结束判断，移除 txt/markdown 文件支持 |
| 1.3.1 | 修复 DataGrid 中无法自动替换的问题 |
| 1.3.0 | 添加替换命中统计 |
| 1.0.0 | 初始版本 |

## 6. 关键实现细节

### 6.1 线程安全

- `CharAutoReplaceAction.replace()` 使用 synchronized 确保并发安全
- 使用 `ApplicationManager.getApplication().invokeLater()` 确保 UI 线程安全

### 6.2 性能优化

- 输入长度 > 5 时直接跳过处理
- 使用 `EditorImpl` 类型检查避免无效处理
- 每次事件处理后移除监听器避免重复执行

### 6.3 兼容性

- 最低支持: IDEA 203.5981 (2020.3)
- 依赖模块: `com.intellij.modules.platform`, `com.intellij.modules.lang`（通用模块，因此兼容 CLion、Rider 等基于 IntelliJ 平台的 IDE）

## 7. 外部资源

- GitHub: [https://github.com/ranbest/ChineseCharReplace](https://github.com/ranbest/ChineseCharReplace)
- Gitee: [https://gitee.com/bestxu/chinese-char-replace](https://gitee.com/bestxu/chinese-char-replace)
- JetBrains Plugin Marketplace: [17345-charautoreplace](https://plugins.jetbrains.com/plugin/17345-charautoreplace)
