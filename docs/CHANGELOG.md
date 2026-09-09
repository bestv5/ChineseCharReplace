# Changelog

## [Unreleased]

### Added

- 注释/字符串识别新增高亮 token 兜底层，改善 C++/SQL/CSS 等语言的识别盲区；并按语言缓存兜底能力，避免每次击键重复探测
- 键入前先提交 PSI 文档：快速连打时上下文判定不再滞后于输入
- 设置页新增帮助主题，按 F1 可打开在线文档；设置页布局改用平台标准 FormBuilder 链式构建，恢复默认入口改为标准 ActionLink

### Changed

- 无法判定上下文的区域（UNKNOWN）兜底由「按代码区策略（默认强制替换）」改为「按纯文本区策略（默认自适应）」：策略控制权从代码区隐式转移到纯文本区，依赖旧行为的用户请在设置中调整纯文本区策略
- 替换提示改用平台标准展示（HintManager.showInformationHint）：定位与隐藏时机为平台默认值，任意按键/滚动会隐藏提示，与旧版精确偏移定位存在差异

### Deprecated

### Removed

- 移除内部 SupportFileType 枚举：无功能影响，纯文本扩展名判定由区域分类器承担

### Fixed

- 全角空格 U+3000 现可按区域策略替换为半角空格（此前被规则链空白过滤误拦截，永不生效）
- 自定义多字符映射键（2–5 字符）替换时不再误删匹配键之前的无关字符（旧行为会把光标前窗口内匹配键之前的字符一并删除）

### Security

## 2.0.0 - 2026-09-08

### Added

- 区域策略引擎：按 CODE/COMMENT/STRING/COMMIT/CONSOLE/PLAIN_TEXT 六区域分别配置替换策略（开/关/自适应）
- CJK 语境自适应检测（CjkContextDetector）：注释与字符串区域内根据光标前文中英文语境启发式决定是否替换
- 三层转换引擎（CharConverter）：自定义映射 > 精选 CJK 标点表 > 全角偏移算术，支持多字符键
- 不可变快照配置模型（Snapshot）：volatile 引用 + O(1) 位图候选探测，读取零锁
- 设置 UI 重建：FormBuilder + JBTable 自定义映射（无行数上限），每区域三态 ComboBox
- 旧配置自动迁移（LegacyConfigMigrator）
- 单步撤销：替换操作封装在 WriteCommandAction 中，一次撤回即可还原

### Changed

- 输入入口从 DocumentListener 改为 charTyped 管线（TypedHandlerDelegate），实时性更好
- 注释识别从硬编码标记改为 PSI + Commenter 兜底（CommentContextResolver）
- 提示服务改为 applicationService 按需获取，去除静态单例
- HTML 转义纯 JDK 实现，无第三方运行时依赖

### Removed

- 删除旧 DocumentListener 方案（CharTypedDocumentLisener）
- 删除旧注释工具类（CnCharCommentUtil）
- 删除旧设置面板（CnCharSettingComponent）
- 删除旧替换动作（CharAutoReplaceAction）、旧配置（ReplaceCharConfig）、旧文档工具（DocumentUtil）

## 1.8.0-beta1 - 2026-09-08

### Added

- 扩展 C/C++ 文件类型支持：新增 .c/.cc/.cxx/.c++/.hpp/.hh/.hxx/.h++/.tpp/.inl/.ipp 等扩展名（原有 .cpp/.h 保持不变），并为其注册对应的注释标记（`//`、`/* ... */`），使基于扩展名的回退检测在这些文件中正确识别注释区域。
- 兼容 Rider/CLion 等 IDE 编辑 C/C++ 文件的场景（插件依赖仍为通用的 platform + lang 模块，无需产品级依赖）。

### Fixed

- 修复6处废弃API调用

## 1.7.1 - 2026-09-08

### Changed

- 构建工具迁移至 gradle-changelog-plugin 生成更新说明

### Fixed

- 修复插件兼容版本号问题：固定 since-build=203 且不设 until-build，取消 IDE 兼容上限
- 移除未随插件打包的 commons-text 依赖，HTML 转义改用纯 JDK 实现，规避运行时 NoClassDefFoundError

## 1.7.0

### Added

- 增加.cpp,.h,.ts,.tsx扩展名支持

## 1.6.1

### Removed

- 移除过时的api

## 1.6.0

### Added

- add auto replace in comment area config (default config will not be replaced in comment). (settings - Tools - CharAutoReplace).

## 1.5.0

### Added

- add show replaced hint config (settings - Tools - CharAutoReplace).

## 1.4.0

### Added

- Added judgment on the end of comment line.

### Removed

- remove txt|markdown file support

## 1.3.1

### Fixed

- Fix problems in DataGrid that cannot be automatically replaced.

## 1.3.0

### Added

- Added replacement Hits.

## 1.0.0

### Added

- hello char auto replacement plugin!
