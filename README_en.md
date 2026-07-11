# BRNTalk

[简体中文](README.md) | [English](README_en.md)

A **Minecraft Forge dialogue-system mod** with an instant-messaging-style interface for story-driven quests, RPG modpacks, and servers.
It provides scriptable conversation threads, branching choices, pause/resume behavior, command actions, and a client-side message UI with HUD notifications.

## Features

- **Hot-reloadable JSON dialogue scripts**: place scripts under `data/<namespace>/brntalk/dialogues/*.json` and reload them with `/reload`.
- **Strict script validation**: broken links, duplicate IDs, empty choices, and infinite TEXT auto-advance loops block the invalid script; online operators receive a summary after `/reload`.
- **Conversation threads**: each player can keep multiple persistent conversation threads synchronized to the client.
- **Three message types**:
  - `text`: normal text messages
  - `choice`: branching choices
  - `wait`: paused nodes that commands can resume
- **Automatic progression**: `text` nodes can advance through `nextId` and `continue`.
- **Command actions**: a message can run a server command through `action` when the node is reached.
- **Client experience**:
  - Press `G` by default to open the BRNTalk screen
  - Select HUD, Toast, or None for new-message notifications
  - Configure UI style, scrolling, typing speed, button position, and more
- **Optional integrations**:
  - Cloth Config configuration screen
  - FTB Library sidebar button
  - JEI extra-area avoidance

---

## Requirements

- **Minecraft**: `1.20.1`
- **Forge**: `47.4.10`
- **Java**: `17`
- Optional dependencies:
  - `cloth_config` for the client configuration screen
  - `ftblibrary` for sidebar-button integration
  - `jei` for extra-area avoidance

---

## Quick start

### 1. Install and start

1. Put the mod jar in the `mods` folder.
2. Start the game and enter a world.
3. Run the following command to start the bundled test conversation:

```mcfunction
/brntalk start test_demo
```

This command requires permission level 2, normally an operator.

### 2. Open the UI

- Press `G` by default.
- Or run the client command:

```mcfunction
/brntalk open_ui
```

### 3. Try choices, pause, and resume

The bundled `test_demo` script includes both `choice` and `wait` nodes.
When the conversation is stopped at a `wait` node, run:

```mcfunction
/brntalk resume @s test_demo
```

---

## Server commands

The server-side `/brntalk` commands below require permission level 2, normally an operator.

### Start a conversation

```mcfunction
/brntalk start <id>
/brntalk start <id> <targets>
```

- Starts the selected script for the command source or target players.

### Clear progress

```mcfunction
/brntalk clear
/brntalk clear <targets>
/brntalk clear <targets> <id>
```

- Clears all progress or only the thread that belongs to a specific script.

### Check whether a node was seen

```mcfunction
/brntalk has_seen <target> <scriptId> <messageId>
```

- Intended for quest conditions and integration with command blocks, data packs, or other mods.

### Resume a WAIT conversation

```mcfunction
/brntalk resume <targets> <scriptId>
/brntalk resume <targets> <scriptId> <messageId>
```

- The second form resumes only threads currently stopped on the selected message ID.

---

## Dialogue script format

BRNTalk loads data-pack resources from:

```text
data/<namespace>/brntalk/dialogues/*.json
```

Two root formats are supported:

1. A **single conversation object** for compatibility with older scripts
2. A **`conversations` array**, which is recommended

### Core fields

- `id`: conversation ID; when omitted, the resource path is used
- `messages`: the message array

Common message fields:

- `id`: message ID; generated automatically when omitted
- `type`: `text`, `choice`, or `wait`; defaults to `text`
- `speakerType`: `npc` or `player`; defaults to `npc`
- `speaker`: displayed speaker name
- `text`: message text
- `nextId`: next message ID
- `continue`: when `true` and `nextId` is omitted, advances to the next array item
- `action`: optional server command executed when this message is reached
- `choices`: used by `choice` messages
  - `id`
  - `text`
  - `nextId`

### Validation rules

The following errors block a dialogue script during server startup or `/reload`:

- Duplicate message IDs
- A `choice` message without any choices
- A message or choice whose `nextId` points to a missing message
- An infinite automatic-progression loop between `text` nodes

After `/reload`, BRNTalk sends a short in-game report to online permission-level-2 players when validation fails. Full details remain available in `latest.log`.

### Example

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
          "text": "Welcome to the server!",
          "nextId": "ask",
          "continue": true
        },
        {
          "id": "ask",
          "type": "choice",
          "speaker": "Guide",
          "text": "What would you like to learn first?",
          "choices": [
            { "id": "a", "text": "The basics", "nextId": "base" },
            { "id": "b", "text": "Classes", "nextId": "job" }
          ]
        },
        {
          "id": "base",
          "type": "text",
          "speaker": "Guide",
          "text": "Visit the notice board in the main city.",
          "action": "/give @s bread 8"
        }
      ]
    }
  ]
}
```

---

## Client configuration

BRNTalk provides client options for:

- Visuals:
  - Vanilla-style background
  - Typing speed (`charDelay`)
  - Delay between consecutive messages (`msgPause`)
- Scrolling:
  - Scroll speed (`scrollRate`)
  - Smoothing factor (`smoothFactor`)
- Open-screen button:
  - Visibility
  - `X/Y` position
- Notification HUD:
  - Mode (`HUD`, `TOAST`, or `NONE`)
  - HUD scale, vertical offset, and safe top margin

When Cloth Config is installed, the mod menu exposes a visual configuration screen.

---

## Server configuration

BRNTalk also provides server settings for validation messages after `/reload`:

- `sendValidationReportInGame`
  - Sends the validation summary to online permission-level-2 players
  - Default: `true`
- `validationReportMaxDetailLines`
  - Maximum number of detail lines sent after the summary
  - Set to `0` for summary-only mode; full details remain in `latest.log`
  - Default: `5`

---

## Integration API

### Java API (`BrntalkAPI`)

Other Java mods can call:

- `startConversation(player, scriptId)`
- `clearAllConversation(player)`
- `clearConversation(player, scriptId)`
- `hasSeen(player, scriptId, messageId)`
- `resumeConversation(player, scriptId, matchMessageId)`

### Event

- `PlayerSeenMessageEvent`
  - Fired when a player reaches or reads a message node
  - Can advance quests, achievements, or other scripted behavior

---

## License

MIT License. See `LICENSE`.

---

## Development and releases

- See [`docs/`](docs/README.md) for project planning, debugging workflows, and release docs.
- The NeoForge control branch maintains the two-version build and publication workflow; see [`docs/RELEASE_WORKFLOW.md`](docs/RELEASE_WORKFLOW.md).

---

## Credits

- Author: Jasdew Starfield
- Art and textures: 嘉轩
