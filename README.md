# BRNTalk

一个面向剧情任务与 RPG 整合包/服务器的即时通讯软件风格 **Minecraft NeoForge 对话系统模组**。  
它提供了可脚本化的对话线程、分支选项、暂停/恢复、命令动作执行，以及客户端消息 UI/HUD 提示。

## 功能概览

- **JSON 对话脚本热重载**：脚本放在 `data/<namespace>/brntalk/dialogues/*.json`，支持 `/reload` 后重载。  
- **对话线程系统**：每名玩家可维护多个会话线程，支持持久化和同步到客户端。  
- **三种消息类型**：
  - `text`：普通文本消息
  - `choice`：分支选项
  - `wait`：暂停节点（可通过命令恢复）
- **自动推进**：`text` 节点可通过 `nextId` 与 `continue` 自动推进。
- **动作执行**：消息可绑定 `action`（服务端命令），在该消息到达时执行。
- **客户端体验**：
  - 按键（默认 `G`）打开 BRNTalk 界面
  - 可选 HUD / Toast / None 新消息提示
  - 可配置 UI 样式、滚动、打字速度、按钮位置等
- **联动支持**：
  - Cloth Config（可选）配置界面
  - FTB Library（可选）侧边栏按钮
  - JEI（可选）界面避让

---

## 环境与依赖

- **Minecraft**: `1.21.1`
- **NeoForge**: `21.1`
- **Java**: `21`
- 可选依赖：
  - `cloth_config`（客户端配置界面）
  - `ftblibrary`（侧边栏按钮集成）
  - `jei`（额外区域避让）

---

## 快速开始

### 1) 安装与启动

1. 将模组放入 `mods` 文件夹。
2. 启动游戏进入世界。
3. 使用以下命令触发测试对话：

```mcfunction
/brntalk start test_demo
```

### 2) 打开 UI

- 默认按键：`G`
- 或客户端命令：

```mcfunction
/brntalk open_ui
```

### 3) 体验分支/暂停/恢复

测试脚本 `test_demo` 中包含 `choice` 与 `wait` 节点。  
当对话停在 `wait` 节点时，可执行：

```mcfunction
/brntalk resume @s test_demo
```

---

## 指令说明（服务端）

### 开启对话

```mcfunction
/brntalk start <id>
/brntalk start <id> <targets>
```

- 为自己或目标玩家触发指定剧本。

### 清除进度

```mcfunction
/brntalk clear
/brntalk clear <targets>
/brntalk clear <targets> <id>
```

- 可清除全部进度，或只清除某个脚本对应的线程。

### 检查是否读过节点

```mcfunction
/brntalk has_seen <target> <scriptId> <messageId>
```

- 用于剧情条件判断（命令方块/数据包/其他模组联动）。

### 继续 WAIT 对话

```mcfunction
/brntalk resume <targets> <scriptId>
/brntalk resume <targets> <scriptId> <messageId>
```

- 第二种可按“当前停留节点 ID”精准过滤恢复目标线程。

---

## 对话脚本格式

BRNTalk 从以下路径读取数据包资源：

```text
data/<namespace>/brntalk/dialogues/*.json
```

支持两种根格式：

1. **单剧本对象**（兼容旧格式）
2. **`conversations` 数组**（推荐）

### 核心字段

- `id`：剧本 ID（不填时会回退到资源路径）
- `messages`：消息数组

每条 message 常用字段：

- `id`：消息 ID（不填会自动生成）
- `type`：`text` / `choice` / `wait`（默认 `text`）
- `speakerType`：`npc` / `player`（默认 `npc`）
- `speaker`：说话者显示名
- `text`：文本内容
- `nextId`：下一个消息 ID
- `continue`：布尔值；若为 `true` 且没写 `nextId`，会自动指向下一条消息
- `action`：到达该消息时执行的服务端命令（可选）
- `choices`：仅 `choice` 类型使用
  - `id`
  - `text`
  - `nextId`

### 示例

```json
{
  "conversations": [
    {
      "id": "intro_quest",
      "messages": [
        {
          "id": "start",
          "type": "text",
          "speaker": "Guide",
          "text": "欢迎来到服务器！",
          "nextId": "ask",
          "continue": true
        },
        {
          "id": "ask",
          "type": "choice",
          "speaker": "Guide",
          "text": "你想先了解什么？",
          "choices": [
            { "id": "a", "text": "基础玩法", "nextId": "base" },
            { "id": "b", "text": "职业系统", "nextId": "job" }
          ]
        },
        {
          "id": "base",
          "type": "text",
          "speaker": "Guide",
          "text": "先去主城公告板看看吧。",
          "action": "/give @s bread 8"
        }
      ]
    }
  ]
}
```

---

## 配置项（客户端）

BRNTalk 提供客户端配置，包含：

- 视觉：
  - 是否使用原版风格背景
  - 打字速度（`charDelay`）
  - 连续消息停顿（`msgPause`）
- 滚动：
  - 滚动速度（`scrollRate`）
  - 平滑系数（`smoothFactor`）
- 开屏按钮：
  - 是否显示按钮
  - 按钮 `X/Y` 坐标
- 提示 HUD：
  - 提示模式（`HUD` / `TOAST` / `NONE`）
  - HUD 缩放、垂直偏移、安全上边距

安装 Cloth Config 后，模组菜单会自动提供可视化配置页。

---

## 对其他模组开放的能力

### API（`BrntalkAPI`）

你可以从其他 Java 模组直接调用：

- `startConversation(player, scriptId)`
- `clearAllConversation(player)`
- `clearConversation(player, scriptId)`
- `hasSeen(player, scriptId, messageId)`
- `resumeConversation(player, scriptId, matchMessageId)`

### 事件

- `PlayerSeenMessageEvent`
  - 当玩家到达/阅读某个消息节点时触发
  - 可用于推进任务、成就或脚本逻辑

---

## 许可证

MIT License（见 `LICENSE`）。

---

## 致谢

- 作者：Jasdew Starfield
- 美术纹理：嘉轩
