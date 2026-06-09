# 更新日志

本文件用于记录 BRNTalk 的版本更新内容。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [未发布]

### 新增

- 暂无。

### 修复

- 暂无。

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
