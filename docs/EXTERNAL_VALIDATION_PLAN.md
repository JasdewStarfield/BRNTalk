# BRNTalk 1.2 External Validation Plan

## 适用范围

- 面向 `1.2.0` 的外部验证、调试脚本和作者文档工作。
- 不修改模组运行时逻辑。
- 不要求发布新的模组版本。
- 默认先服务 `mc/1.21.1-neoforge` 工作树，再把可复用脚本和 fixture 同步到 `mc/1.20.1-forge`。

## 阶段一：Fixture 与调试判定基础

### 目标

- 把“手动拷脚本、手动看日志”的流程变成可重复执行的仓库工作流。
- 给后续模组内校验增强提供稳定输入样本。
- 让每次调试都能输出明确结论：`PASS`、`FAIL` 或 `UNKNOWN`。

### 不做

- 不新增对话脚本语义。
- 不改 `ConversationLoader`、`ConversationValidator`、`TalkManager` 等模组代码。
- 不把调试 fixture 放进 `src/main/resources`。
- 不做完整客户端自动化。

## 交付内容

### 1. Fixture 目录结构

目标结构：

```text
validation_fixtures/
  README.md
  invalid_dialogues/
    README.md
    duplicate_message_id.json
    missing_next_id.json
    empty_choice_node.json
    duplicate_choice_id.json
    infinite_text_loop.json
  valid_dialogues/
    README.md
    basic_linear.json
    choice_branch.json
    wait_resume.json
    action_command.json
    multi_thread.json
```

要求：

- `invalid_dialogues` 只放应被拒绝加载的脚本。
- `valid_dialogues` 只放应能成功加载并用于 smoke 验证的脚本。
- 每个 fixture 在 README 中写清用途、执行命令、预期日志和通过条件。

### 2. 有效脚本 fixture

第一批有效脚本：

- `basic_linear.json`
  - 覆盖普通 `text` 节点和自动推进。
  - 推荐命令：`/brntalk start brntalk_debug:basic_linear`
- `choice_branch.json`
  - 覆盖 `choice` 节点和固定分支。
  - 推荐命令：`/brntalk start brntalk_debug:choice_branch`
- `wait_resume.json`
  - 覆盖 `wait` 节点和 `/brntalk resume`。
  - 推荐命令：`/brntalk start brntalk_debug:wait_resume`
- `action_command.json`
  - 覆盖 `action` 执行链路。
  - 推荐使用低风险命令，例如 `tellraw` 或 `say`。
- `multi_thread.json`
  - 覆盖多会话线程和列表同步 smoke。

### 3. Fixture 注入脚本增强

目标文件：`tools/debug/use-dialogue-fixture.ps1`

新增能力：

- `-Type invalid|valid`
  - 默认可设为 `invalid`，保持旧流程直觉。
- `-Fixture <name>`
  - 支持不带 `.json` 的 fixture 名。
- `-List`
  - 列出可用 fixture，并显示所属类型。
- `-SourcePath <path>`
  - 保持现有能力，用于临时脚本。

示例：

```powershell
.\tools\debug\use-dialogue-fixture.ps1 -Type valid -Fixture basic_linear
.\tools\debug\use-dialogue-fixture.ps1 -Type invalid -Fixture missing_next_id
.\tools\debug\use-dialogue-fixture.ps1 -List
```

通过条件：

- 能注入有效和无效 fixture。
- 注入前仍会清理旧 debug dialogue。
- 输出 debug datapack 目标路径和下一步操作。

### 4. 日志读取脚本增强

目标文件：`tools/debug/read-brntalk-log.ps1`

新增能力：

- `-ExpectPattern <regex[]>`
  - 至少有一条匹配时判为满足预期。
- `-RejectPattern <regex[]>`
  - 出现任意匹配时判为失败。
- `-FailOnValidationError`
  - 出现 validation error 或 skipped script 时判为失败。
- 输出最终判定：
  - `PASS`
  - `FAIL`
  - `UNKNOWN`

示例：

```powershell
.\tools\debug\read-brntalk-log.ps1 -ExpectPattern 'Loaded conversation'
.\tools\debug\read-brntalk-log.ps1 -ExpectPattern 'Skipping script' -SummaryOnly
.\tools\debug\read-brntalk-log.ps1 -FailOnValidationError
```

通过条件：

- 保留现有摘要统计。
- 没有提供判定参数时仍只读日志，不强行判断。
- 有判定参数时必须给出最终结论。

### 5. Smoke 工作流脚本

新增文件：`tools/debug/run-dialogue-smoke.ps1`

第一阶段只做半自动 smoke，不操控客户端。

建议能力：

- `-Type invalid|valid`
- `-Fixture <name>`
- `-StartServer`
- `-NoBuild`
- `-ExpectPattern <regex[]>`
- `-RejectPattern <regex[]>`

执行流程：

1. 清理 debug datapack。
2. 注入指定 fixture。
3. 输出需要在游戏内执行的命令。
4. 可选启动 debug server。
5. 读取日志并输出 `PASS`、`FAIL` 或 `UNKNOWN`。

示例：

```powershell
.\tools\debug\run-dialogue-smoke.ps1 -Type invalid -Fixture missing_next_id -ExpectPattern 'Skipping script'
.\tools\debug\run-dialogue-smoke.ps1 -Type valid -Fixture basic_linear -RejectPattern 'Skipping script'
```

通过条件：

- 无效 fixture 能验证“应被拒绝”的路径。
- 有效 fixture 能验证“至少不应出现 validation error”的路径。
- 无法自动确认的客户端步骤必须输出明确的人工下一步。

### 5.1 RCON 自动验证脚本

新增文件：

- `tools/debug/invoke-rcon-command.ps1`
- `tools/debug/run-rcon-smoke.ps1`

目标：

- 自动启用本地 debug server 的 RCON 配置。
- 自动注入 fixture。
- 自动执行 `reload`。
- 自动读取日志并输出 `PASS`、`FAIL` 或 `UNKNOWN`。
- 当指定 `-PlayerName` 且该玩家在线时，自动执行 `/brntalk start` 和 `/brntalk resume` 这类运行时命令。

示例：

```powershell
.\tools\debug\run-debug-server.ps1 -EnableRcon -AcceptEula -NoBuild
.\tools\debug\run-rcon-smoke.ps1 -Type invalid -Fixture missing_next_id
.\tools\debug\run-rcon-smoke.ps1 -Type valid -Fixture basic_linear
.\tools\debug\run-rcon-smoke.ps1 -Type valid -Fixture wait_resume -PlayerName DevPlayer
```

说明：

- RCON 命令使用服务端控制台语法，不带 `/`。
- 没有 `-PlayerName` 时，有效 fixture 只验证加载和日志，不执行玩家对话流程。
- `choice_branch` 可以通过 RCON 自动开始线程，但选择选项仍需要客户端 UI。
- `-AcceptEula` 只在你明确传入时写入 `run/eula.txt`。

### 6. 阶段一 README 更新

需要更新：

- `validation_fixtures/README.md`
- `validation_fixtures/invalid_dialogues/README.md`
- `validation_fixtures/valid_dialogues/README.md`
- `PHASE1_DEBUGGING_PLAN.md`

文档要求：

- 明确 fixture 不会进入正式资源。
- 明确 debug datapack 路径。
- 明确每个 fixture 的预期结果。
- 把旧的 Phase 1 说法改成“1.2 外部验证工作流”，避免和版本路线混淆。

## 推荐提交顺序

1. 新增 `valid_dialogues` fixture 和 README。
2. 新增顶层 `validation_fixtures/README.md`。
3. 增强 `use-dialogue-fixture.ps1`。
4. 增强 `read-brntalk-log.ps1`。
5. 新增 `run-dialogue-smoke.ps1`。
6. 更新调试流程文档。

## 阶段一完成标准

- 可以用一条命令列出所有 fixture。
- 可以用一条命令注入任意有效或无效 fixture。
- 可以用日志脚本对常见结果输出 `PASS`、`FAIL` 或 `UNKNOWN`。
- 有效和无效 fixture 都有清楚的预期结果说明。
- 整个阶段不需要发布新的 BRNTalk 模组版本。
