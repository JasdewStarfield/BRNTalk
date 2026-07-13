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
    $fixtureRoot = Join-Path $repoRoot 'validation_fixtures'
    $invalidFixturesDir = Join-Path $fixtureRoot 'invalid_dialogues'
    $validFixturesDir = Join-Path $fixtureRoot 'valid_dialogues'

    return [PSCustomObject]@{
        RepoRoot         = $repoRoot
        RunDir           = $runDir
        LogDir           = Join-Path $runDir 'logs'
        LatestLogPath    = Join-Path $runDir 'logs\latest.log'
        ServerPropertiesPath = Join-Path $runDir 'server.properties'
        EulaPath         = Join-Path $runDir 'eula.txt'
        DatapacksDir     = $datapacksDir
        DebugPackRoot    = $debugPackRoot
        DebugDialogueDir = $debugDialogueDir
        PackMetaPath     = Join-Path $debugPackRoot 'pack.mcmeta'
        FixtureRoot      = $fixtureRoot
        FixturesDir      = $invalidFixturesDir
        InvalidFixturesDir = $invalidFixturesDir
        ValidFixturesDir = $validFixturesDir
        ServerConfigDir  = Join-Path $runDir 'world\serverconfig'
    }
}

function Write-BrntalkUtf8File {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [string]$Content
    )

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Get-BrntalkFixtureDirectory {
    param(
        [Parameter(Mandatory)]
        [pscustomobject]$Context,

        [Parameter(Mandatory)]
        [ValidateSet('invalid', 'valid')]
        [string]$Type
    )

    if ($Type -eq 'valid') {
        return $Context.ValidFixturesDir
    }

    return $Context.InvalidFixturesDir
}

function Get-BrntalkFixtureFiles {
    param(
        [Parameter(Mandatory)]
        [pscustomobject]$Context,

        [ValidateSet('invalid', 'valid', 'all')]
        [string]$Type = 'all'
    )

    $types = if ($Type -eq 'all') { @('invalid', 'valid') } else { @($Type) }
    foreach ($fixtureType in $types) {
        $directory = Get-BrntalkFixtureDirectory -Context $Context -Type $fixtureType
        if (-not (Test-Path -LiteralPath $directory)) {
            continue
        }

        Get-ChildItem -LiteralPath $directory -Filter '*.json' -File |
            Sort-Object Name |
            ForEach-Object {
                [PSCustomObject]@{
                    Type = $fixtureType
                    Name = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
                    Path = $_.FullName
                }
            }
    }
}

function Set-BrntalkPropertyLine {
    param(
        [Parameter(Mandatory)]
        [string[]]$Lines,

        [Parameter(Mandatory)]
        [string]$Key,

        [Parameter(Mandatory)]
        [string]$Value
    )

    $result = New-Object System.Collections.Generic.List[string]
    $found = $false
    $pattern = '^\s*' + [regex]::Escape($Key) + '\s*='

    foreach ($line in $Lines) {
        if ($line -match $pattern) {
            $result.Add("$Key=$Value")
            $found = $true
        } else {
            $result.Add($line)
        }
    }

    if (-not $found) {
        $result.Add("$Key=$Value")
    }

    return $result.ToArray()
}

function Enable-BrntalkDebugRcon {
    param(
        [Parameter(Mandatory)]
        [pscustomobject]$Context,

        [string]$Password = 'brntalk-debug',

        [int]$Port = 25575,

        [switch]$AcceptEula
    )

    Ensure-BrntalkDirectory -Path $Context.RunDir

    $lines = if (Test-Path -LiteralPath $Context.ServerPropertiesPath) {
        @(Get-Content -LiteralPath $Context.ServerPropertiesPath)
    } else {
        @()
    }

    $lines = Set-BrntalkPropertyLine -Lines $lines -Key 'enable-rcon' -Value 'true'
    $lines = Set-BrntalkPropertyLine -Lines $lines -Key 'rcon.password' -Value $Password
    $lines = Set-BrntalkPropertyLine -Lines $lines -Key 'rcon.port' -Value ([string]$Port)

    Write-BrntalkUtf8File -Path $Context.ServerPropertiesPath -Content (($lines -join [Environment]::NewLine) + [Environment]::NewLine)

    if ($AcceptEula) {
        Write-BrntalkUtf8File -Path $Context.EulaPath -Content ("eula=true" + [Environment]::NewLine)
    }
}

function Read-BrntalkExactBytes {
    param(
        [Parameter(Mandatory)]
        [System.IO.Stream]$Stream,

        [Parameter(Mandatory)]
        [int]$Length
    )

    $buffer = New-Object byte[] $Length
    $offset = 0
    while ($offset -lt $Length) {
        $read = $Stream.Read($buffer, $offset, $Length - $offset)
        if ($read -le 0) {
            throw 'RCON connection closed while reading a packet.'
        }

        $offset += $read
    }

    Write-Output -NoEnumerate $buffer
}

function Send-BrntalkRconPacket {
    param(
        [Parameter(Mandatory)]
        [System.IO.Stream]$Stream,

        [Parameter(Mandatory)]
        [int]$RequestId,

        [Parameter(Mandatory)]
        [int]$Type,

        [Parameter(Mandatory)]
        [string]$Payload
    )

    # Minecraft RCON packets are little-endian: length, request id, type, UTF-8 payload, two null bytes.
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($Payload)
    $length = 4 + 4 + $payloadBytes.Length + 2
    $packet = New-Object byte[] (4 + $length)

    [System.Buffer]::BlockCopy([BitConverter]::GetBytes([int]$length), 0, $packet, 0, 4)
    [System.Buffer]::BlockCopy([BitConverter]::GetBytes([int]$RequestId), 0, $packet, 4, 4)
    [System.Buffer]::BlockCopy([BitConverter]::GetBytes([int]$Type), 0, $packet, 8, 4)
    [System.Buffer]::BlockCopy($payloadBytes, 0, $packet, 12, $payloadBytes.Length)

    $Stream.Write($packet, 0, $packet.Length)
}

function Receive-BrntalkRconPacket {
    param(
        [Parameter(Mandatory)]
        [System.IO.Stream]$Stream
    )

    $lengthBytes = Read-BrntalkExactBytes -Stream $Stream -Length 4
    $length = [BitConverter]::ToInt32($lengthBytes, 0)
    $body = Read-BrntalkExactBytes -Stream $Stream -Length $length
    $payloadLength = [Math]::Max(0, $length - 10)

    return [PSCustomObject]@{
        RequestId = [BitConverter]::ToInt32($body, 0)
        Type = [BitConverter]::ToInt32($body, 4)
        Payload = [System.Text.Encoding]::UTF8.GetString($body, 8, $payloadLength)
    }
}

function Invoke-BrntalkRconCommand {
    param(
        [string]$HostName = '127.0.0.1',

        [int]$Port = 25575,

        [Parameter(Mandatory)]
        [string]$Password,

        [Parameter(Mandatory)]
        [string]$Command,

        [int]$TimeoutMs = 5000
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connectTask = $client.ConnectAsync($HostName, $Port)
        if (-not $connectTask.Wait($TimeoutMs)) {
            throw "Timed out connecting to RCON at ${HostName}:$Port."
        }

        $client.ReceiveTimeout = $TimeoutMs
        $client.SendTimeout = $TimeoutMs
        $stream = $client.GetStream()

        Send-BrntalkRconPacket -Stream $stream -RequestId 1 -Type 3 -Payload $Password
        $authResponse = Receive-BrntalkRconPacket -Stream $stream
        if ($authResponse.RequestId -eq -1) {
            throw 'RCON authentication failed.'
        }

        Send-BrntalkRconPacket -Stream $stream -RequestId 2 -Type 2 -Payload $Command
        $response = Receive-BrntalkRconPacket -Stream $stream
        return $response.Payload
    } finally {
        $client.Close()
    }
}

function Wait-BrntalkRcon {
    param(
        [string]$HostName = '127.0.0.1',

        [int]$Port = 25575,

        [Parameter(Mandatory)]
        [string]$Password,

        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            Invoke-BrntalkRconCommand -HostName $HostName -Port $Port -Password $Password -Command 'list' | Out-Null
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for RCON at ${HostName}:$Port."
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
    # This pack metadata matches the Forge/Minecraft 1.20.1 runtime used by this worktree.
    return @'
{
  "pack": {
    "description": "BRNTalk debug datapack",
    "pack_format": 15
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
