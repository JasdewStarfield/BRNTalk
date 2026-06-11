Set-StrictMode -Version Latest

function Get-BrntalkDebugContext {
    param(
        [Parameter(Mandatory)]
        [string]$ScriptRoot
    )

    # All debug scripts live under tools/debug, so two parent hops land on the repo root.
    $repoRoot = (Resolve-Path (Join-Path $ScriptRoot '..\..')).Path
    $runDir = Join-Path $repoRoot 'run'
    $datapacksDir = Join-Path $runDir 'world\datapacks'
    $debugPackRoot = Join-Path $datapacksDir 'brntalk_debug'
    $debugDialogueDir = Join-Path $debugPackRoot 'data\brntalk_debug\brntalk\dialogues'
    $fixturesDir = Join-Path $repoRoot 'validation_fixtures\invalid_dialogues'

    return [PSCustomObject]@{
        RepoRoot         = $repoRoot
        RunDir           = $runDir
        LogDir           = Join-Path $runDir 'logs'
        LatestLogPath    = Join-Path $runDir 'logs\latest.log'
        DatapacksDir     = $datapacksDir
        DebugPackRoot    = $debugPackRoot
        DebugDialogueDir = $debugDialogueDir
        PackMetaPath     = Join-Path $debugPackRoot 'pack.mcmeta'
        FixturesDir      = $fixturesDir
        ServerConfigDir  = Join-Path $runDir 'world\serverconfig'
    }
}

function Ensure-BrntalkDirectory {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Get-BrntalkDebugPackMetaJson {
    # This pack metadata matches the current NeoForge/Minecraft 1.21.1 runtime.
    return @'
{
  "pack": {
    "description": "BRNTalk debug datapack",
    "pack_format": 48,
    "supported_formats": [
      0,
      2147483647
    ]
  }
}
'@
}

function Ensure-BrntalkDebugDatapack {
    param(
        [Parameter(Mandatory)]
        [pscustomobject]$Context
    )

    Ensure-BrntalkDirectory -Path $Context.DatapacksDir
    Ensure-BrntalkDirectory -Path $Context.DebugPackRoot
    Ensure-BrntalkDirectory -Path $Context.DebugDialogueDir

    # Always rewrite pack.mcmeta so the datapack stays deterministic.
    Set-Content -LiteralPath $Context.PackMetaPath -Value (Get-BrntalkDebugPackMetaJson) -Encoding UTF8
}

function Clear-BrntalkDebugDialogues {
    param(
        [Parameter(Mandatory)]
        [pscustomobject]$Context
    )

    if (-not (Test-Path -LiteralPath $Context.DebugDialogueDir)) {
        return
    }

    Get-ChildItem -LiteralPath $Context.DebugDialogueDir -Filter '*.json' -File -ErrorAction SilentlyContinue |
        Remove-Item -Force
}

function Invoke-BrntalkGradle {
    param(
        [Parameter(Mandatory)]
        [pscustomobject]$Context,

        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    Push-Location $Context.RepoRoot
    try {
        & .\gradlew.bat @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle exited with code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}
