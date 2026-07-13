# Valid Dialogue Fixtures

These scripts should load successfully. Use them for smoke checks after debug datapack injection.

Install one fixture at a time:

```powershell
.\tools\debug\use-dialogue-fixture.ps1 -Type valid -Fixture basic_linear
```

Then run `/reload` or restart the debug server.

## Fixtures

| Fixture | Purpose | In-game command | Expected result |
| --- | --- | --- | --- |
| `basic_linear.json` | Text nodes and auto-advance | `/brntalk start brntalk_debug:basic_linear` | A short linear thread appears and reaches the final message. |
| `choice_branch.json` | Choice node and branch selection | `/brntalk start brntalk_debug:choice_branch` | The UI shows two options; either option reaches `end`. |
| `wait_resume.json` | Wait node and filtered resume | `/brntalk start brntalk_debug:wait_resume`, then `/brntalk resume @s brntalk_debug:wait_resume wait_node` | The paused thread continues to `resumed`. |
| `action_command.json` | Low-risk action execution | `/brntalk start brntalk_debug:action_command` | The server runs `/say BRNTalk action fixture reached`. |
| `multi_thread.json` | Multiple conversations from one file | `/brntalk start brntalk_debug:multi_thread_alpha`, then `/brntalk start brntalk_debug:multi_thread_beta` | Two separate threads appear in the thread list. |

## Log Checks

Valid fixtures should not produce validation, skipped-script, or file-load failure lines:

```powershell
.\tools\debug\read-brntalk-log.ps1 -FailOnValidationError -SummaryOnly
```

RCON smoke can run server-side reload checks automatically:

```powershell
.\tools\debug\run-rcon-smoke.ps1 -Type valid -Fixture basic_linear
```

If a player is online, pass the player name to run supported `/brntalk` commands:

```powershell
.\tools\debug\run-rcon-smoke.ps1 -Type valid -Fixture wait_resume -PlayerName DevPlayer
```

`choice_branch` still requires manual option selection in the client UI.
