# 1.2 External Validation Workflow

## 适用范围

- 适用于 `1.2.0` 的外部验证、fixture、日志和调试脚本工作。
- 适用于脚本校验、运行时 smoke、持久化 / 同步 smoke、客户端表现复核。
- 不涉及模组版本发布。
- 不把临时调试内容并入正式资源。

## 基本原则

### 1. 先分类，再调试

每次开始前，先判断问题属于哪一类：

- 脚本校验问题。
- 运行时推进问题。
- 持久化 / 同步问题。
- 客户端表现问题。

不要一上来同时检查所有链路。

### 2. 默认先回到干净状态

除非正在复现残留状态问题，否则开始前默认：

1. 清理调试 datapack。
2. 只注入本次需要的 fixture。
3. 明确本次验证目标。

推荐顺序：

```powershell
.\tools\debug\clear-debug-datapack.ps1
.\tools\debug\use-dialogue-fixture.ps1 -Type valid -Fixture basic_linear
```

### 3. 一次只验证一个目标

每次调试只回答一个明确问题，例如：

- 这个无效脚本是否会被拦住。
- 这个有效脚本是否能加载。
- 这个 `wait` 节点是否能正确恢复。
- 这个 `action` 是否会执行。
- `/reload` 后状态是否正确。

### 4. 先看现象和日志，再看代码

默认顺序：

1. 复现问题。
2. 看命令反馈。
3. 看 `run/logs/latest.log`。
4. 再回源码定位。

### 5. 先确认服务端事实，再判断客户端表现

如果同时涉及服务端和客户端，默认先确认：

- 脚本是否成功加载。
- 线程是否成功创建或推进。
- 日志是否出现预期记录。

确认服务端正确后，再看 UI / HUD / Toast 是否有问题。

### 6. 调试注入物不得进入正式资源

`1.2.0` 外部验证默认约定：

- 不直接改 `src/main/resources` 做临时验证。
- 不把无效脚本放进正式资源目录。
- 所有临时脚本统一放进 debug datapack：

```text
run/world/datapacks/brntalk_debug/data/brntalk_debug/brntalk/dialogues/
```

### 7. 每次调试都要有结论

每次调试结束都要能明确归类为：

- `PASS`
- `FAIL`
- `UNKNOWN`

至少记录：

- 验证目标。
- 使用的 fixture。
- 执行的命令。
- 关键日志。
- 最终判定。

## 标准操作流程

### A. 脚本校验流程

适用场景：

- 校验无效脚本。
- 调整 `ConversationLoader`。
- 调整 `ConversationValidator`。

步骤：

1. `clear-debug-datapack.ps1`
2. `use-dialogue-fixture.ps1 -Type invalid -Fixture <name>`
3. 启动服务端或执行 `/reload`
4. `read-brntalk-log.ps1 -ExpectPattern 'Validation:', 'Skipping script'`
5. 判断是否出现预期错误和 `Skipping script`
6. 清理调试 datapack

### B. 有效脚本 smoke 流程

适用场景：

- 确认 fixture 能成功加载。
- 确认脚本格式和基础运行链路没有被破坏。

步骤：

1. `clear-debug-datapack.ps1`
2. `use-dialogue-fixture.ps1 -Type valid -Fixture <name>`
3. 启动服务端或执行 `/reload`
4. 执行 fixture README 中列出的 `/brntalk start ...` 命令。
5. `read-brntalk-log.ps1 -FailOnValidationError`
6. 如异常，再查完整 `latest.log`。

### C. 运行时对话流程

适用场景：

- 调整 `TalkManager`。
- 调整 `BrntalkAPI`。
- 调整 `/brntalk` 命令行为。

步骤：

1. 保证只加载一份明确的有效 fixture。
2. 执行 `/brntalk start <id>`。
3. 如涉及分支，选择固定路径。
4. 如涉及暂停，执行 `/brntalk resume`。
5. 检查消息推进、`wait`、`choice`、`action`。
6. 如异常，再查 `latest.log`。

### D. 持久化与同步流程

适用场景：

- 调整 `PlayerTalkState`。
- 调整 `SyncEventListener`。
- 调整网络同步逻辑。

步骤：

1. 清空旧调试状态。
2. 启动对话并推进到中间状态。
3. 退出重进或执行 `/reload`。
4. 检查线程恢复、未读状态、客户端一致性。

### E. 半自动 smoke 脚本流程

适用场景：

- 快速准备 debug datapack。
- 输出人工游戏内步骤。
- 用日志给出初步 `PASS` / `FAIL` / `UNKNOWN`。

示例：

```powershell
.\tools\debug\run-dialogue-smoke.ps1 -Type invalid -Fixture missing_next_id
.\tools\debug\run-dialogue-smoke.ps1 -Type valid -Fixture basic_linear
```

### F. RCON 自动 smoke 流程

适用场景：

- 自动执行 `/reload`。
- 自动验证 invalid fixture 会被拒绝。
- 自动验证 valid fixture 不产生校验错误。
- 当玩家在线时，自动执行基础 `/brntalk start` 和 `/brntalk resume` 流程。

启动带 RCON 的 debug server：

```powershell
.\tools\debug\run-debug-server.ps1 -EnableRcon -AcceptEula -NoBuild
```

服务端启动后，在另一个终端运行：

```powershell
.\tools\debug\run-rcon-smoke.ps1 -Type invalid -Fixture missing_next_id
.\tools\debug\run-rcon-smoke.ps1 -Type valid -Fixture basic_linear
.\tools\debug\run-rcon-smoke.ps1 -Type valid -Fixture wait_resume -PlayerName DevPlayer
```

约束：

- `-PlayerName` 需要该玩家已在 debug server 在线。
- `choice_branch` 的选项点击仍由客户端手动测试。
- 本流程不做客户端截图或 UI 识别。

## 调试脚本使用约定

### 命名

- 放在 `tools/debug/`。
- 使用动词开头：
  - `run-...`
  - `use-...`
  - `clear-...`
  - `read-...`

### 输入输出

- 输入尽量显式。
- 输出必须带关键路径。
- 失败时要给出下一步提示。

### 状态管理

- 尽量幂等。
- 默认先清理再写入。
- 不修改正式资源目录。
- 所有调试注入物集中在固定 debug datapack。

### 结果判断

- 优先输出是否符合预期。
- 不只输出原始日志。
- 能自动判断时输出 `PASS` / `FAIL` / `UNKNOWN`。
