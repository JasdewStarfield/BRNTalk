# 更新日志

本文件用于记录 BRNTalk 的版本更新内容。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [未发布]

### 新增

- 暂无。

### 修复

- 暂无。

## [1.0.1] - 2026-05-21

### 安全性

- 限制服务端 `/brntalk` 管理命令的执行权限，现在需要 2 级权限（通常为 OP）才能使用。
- 修复普通玩家可能通过 `/brntalk start` 等命令间接触发对话脚本 `action` 的生产环境风险。

### 修复

- 修复从分支选项或 `wait` 节点恢复后，目标消息节点自身的 `action` 不会执行的问题。
