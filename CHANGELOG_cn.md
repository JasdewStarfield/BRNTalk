# 更新日志

本文件用于记录 BRNTalk 的版本更新内容。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [未发布]

### 新增

- 暂无。

### 修复

- 暂无。

## [1.1.0] - 2026-07-08

### 变更

- 对项目进行较大规模的多版本重构，整理 1.21.1 NeoForge 与 1.20.1 Forge 双分支维护方式。
- 将平台相关逻辑集中到 Platform 包，减少核心运行时代码对具体加载器 API 的直接依赖。
- 发布产物文件名现在包含目标 Minecraft 版本，便于区分同一 BRNTalk 版本号下的不同 MC 分支构建。

### 修复

- 1.20.1 分支完成 Forge 与低版本适配，修正 Java 17 API 差异、Forge 网络与运行时差异、资源包 metadata、旧存档数据迁移提示以及对话 UI 列表/滚动条表现问题。

## [1.0.2] - 2026-06-10

### 新增

- `/reload` 后如果 BRNTalk 对话脚本校验失败，会向在线的 2 级权限玩家发送游戏内摘要提示，并提示到 `latest.log` 查看完整细节。
- 新增服务端配置项 `sendValidationReportInGame` 与 `validationReportMaxDetailLines`，可控制是否发送此类游戏内提示以及最多发送多少条详情。

### 修复

- 对话脚本加载改为严格校验模式：断链、重复 ID、空 choice、TEXT 自动推进死循环会阻止脚本进入运行时。

## [1.0.1] - 2026-05-21

### 安全性

- 限制服务端 `/brntalk` 管理命令的执行权限，现在需要 2 级权限（通常为 OP）才能使用。
- 修复普通玩家可能通过 `/brntalk start` 等命令间接触发对话脚本 `action` 的生产环境风险。

### 修复

- 修复从分支选项或 `wait` 节点恢复后，目标消息节点自身的 `action` 不会执行的问题。
