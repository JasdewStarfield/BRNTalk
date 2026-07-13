# Invalid Dialogue Fixtures

These files are intentionally invalid BRNTalk dialogue scripts for validation testing.
They are stored outside `src/main/resources`, so they are not packaged into the mod jar.

Install one fixture at a time:

```powershell
.\tools\debug\use-dialogue-fixture.ps1 -Type invalid -Fixture missing_next_id
```

Then run `/reload` or restart the debug server.

## Fixtures

| Fixture | Purpose | Expected result |
| --- | --- | --- |
| `duplicate_message_id.json` | Reuses one message ID in a script. | BRNTalk logs a duplicate message ID validation error and skips the script. |
| `missing_next_id.json` | Points `nextId` at a missing message. | BRNTalk logs a missing message validation error and skips the script. |
| `empty_choice_node.json` | Defines a `choice` node with no choices. | BRNTalk logs an empty choice validation error and skips the script. |
| `duplicate_choice_id.json` | Reuses one choice ID in a choice node. | BRNTalk logs a duplicate choice ID validation error and skips the script. |
| `infinite_text_loop.json` | Creates an infinite text auto-advance loop. | BRNTalk logs an automatic text loop validation error and skips the script. |

## Log Checks

Invalid fixtures pass when the log contains `Validation:` and `Skipping script`:

```powershell
.\tools\debug\read-brntalk-log.ps1 -ExpectPattern 'Validation:', 'Skipping script' -SummaryOnly
```

RCON smoke can inject the fixture, run `reload`, and check the log automatically:

```powershell
.\tools\debug\run-rcon-smoke.ps1 -Type invalid -Fixture missing_next_id
```
