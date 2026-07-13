# Warning Dialogue Fixtures

These scripts should load successfully while producing validation warnings in `latest.log`.

Install one fixture at a time:

```powershell
.\tools\debug\use-dialogue-fixture.ps1 -Type warning -Fixture blank_message_text
```

Then run `/reload` or restart the debug server.

## Fixtures

| Fixture | Purpose | Expected result |
| --- | --- | --- |
| `blank_message_text.json` | A message has empty or whitespace-only text. | BRNTalk logs a warning and keeps the script loaded. |
| `blank_choice_text.json` | A choice has empty or whitespace-only text. | BRNTalk logs a warning and keeps the script loaded. |
| `wait_without_next.json` | A `wait` node has no `nextId`. | BRNTalk logs that `/brntalk resume` cannot advance the node. |
| `unreachable_message.json` | A message is not reachable from the first message. | BRNTalk logs an unreachable-node warning and keeps the script loaded. |

## Log Checks

Warning fixtures pass when the log contains `Validation:` and `WARNING`, without `Skipping script` or file-load failure lines:

```powershell
.\tools\debug\run-rcon-smoke.ps1 -Type warning -Fixture blank_message_text
```
