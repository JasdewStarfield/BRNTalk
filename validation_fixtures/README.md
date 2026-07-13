# BRNTalk Validation Fixtures

These fixtures are repo-local dialogue scripts for debug and smoke workflows.
They are intentionally kept outside `src/main/resources`, so they are not packaged into the mod jar.

Debug scripts install one fixture at a time into:

```text
run/world/datapacks/brntalk_debug/data/brntalk_debug/brntalk/dialogues/
```

## Fixture Types

- [`invalid_dialogues/`](invalid_dialogues/README.md): scripts that should fail validation and be skipped.
- [`valid_dialogues/`](valid_dialogues/README.md): scripts that should load successfully and support smoke checks.

## Common Commands

List available fixtures:

```powershell
.\tools\debug\use-dialogue-fixture.ps1 -List
```

Install an invalid fixture:

```powershell
.\tools\debug\use-dialogue-fixture.ps1 -Type invalid -Fixture missing_next_id
```

Install a valid fixture:

```powershell
.\tools\debug\use-dialogue-fixture.ps1 -Type valid -Fixture basic_linear
```

After installation, run `/reload` in the debug server or restart the server, then inspect logs:

```powershell
.\tools\debug\read-brntalk-log.ps1 -Tail -TailCount 80
```

Run an RCON-driven smoke check against a debug server with RCON enabled:

```powershell
.\tools\debug\run-rcon-smoke.ps1 -Type invalid -Fixture missing_next_id
.\tools\debug\run-rcon-smoke.ps1 -Type valid -Fixture basic_linear
.\tools\debug\run-rcon-smoke.ps1 -Type valid -Fixture wait_resume -PlayerName DevPlayer
```

## Result Rules

- Invalid fixtures pass when BRNTalk reports validation errors and skips the script.
- Valid fixtures pass when BRNTalk loads the script and no validation, skipped-script, or file-load failure lines appear.
- Runtime fixtures still need the listed in-game commands because this first phase does not automate the Minecraft client.
