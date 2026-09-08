# Changelog

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

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
