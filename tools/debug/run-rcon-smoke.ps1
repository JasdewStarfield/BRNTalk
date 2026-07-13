[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)]
    [ValidateSet('invalid', 'valid', 'warning')]
    [string]$Type,

    [Parameter(Mandatory)]
    [string]$Fixture,

    [string]$PlayerName,
    [switch]$StartServer,
    [switch]$StopServer,
    [switch]$NoBuild,
    [switch]$AcceptEula,
    [string]$HostName = '127.0.0.1',
    [int]$RconPort = 25575,
    [string]$RconPassword = 'brntalk-debug',
    [int]$CommandDelaySeconds = 2,
    [int]$ServerWaitSeconds = 120
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'DebugTools.Common.ps1')

function Get-BrntalkRconSmokeCommands {
    param(
        [Parameter(Mandatory)]
        [string]$FixtureName,

        [string]$TargetPlayer
    )

    $commands = New-Object System.Collections.Generic.List[string]
    $commands.Add('reload')

    if ([string]::IsNullOrWhiteSpace($TargetPlayer)) {
        return $commands.ToArray()
    }

    switch ($FixtureName) {
        'basic_linear' {
            $commands.Add("brntalk start brntalk_debug:basic_linear $TargetPlayer")
        }
        'choice_branch' {
            $commands.Add("brntalk start brntalk_debug:choice_branch $TargetPlayer")
        }
        'wait_resume' {
            $commands.Add("brntalk start brntalk_debug:wait_resume $TargetPlayer")
            $commands.Add("brntalk resume $TargetPlayer brntalk_debug:wait_resume wait_node")
        }
        'action_command' {
            $commands.Add("brntalk start brntalk_debug:action_command $TargetPlayer")
        }
        'multi_thread' {
            $commands.Add("brntalk start brntalk_debug:multi_thread_alpha $TargetPlayer")
            $commands.Add("brntalk start brntalk_debug:multi_thread_beta $TargetPlayer")
        }
    }

    return $commands.ToArray()
}

$context = Get-BrntalkDebugContext -ScriptRoot $PSScriptRoot
$fixtureName = [System.IO.Path]::GetFileNameWithoutExtension($Fixture)
$serverProcess = $null

Write-Host "[BRNTalk RCON Smoke] Fixture: $Type/$fixtureName"
Write-Host "[BRNTalk RCON Smoke] RCON: ${HostName}:$RconPort"

Enable-BrntalkDebugRcon -Context $context -Password $RconPassword -Port $RconPort -AcceptEula:$AcceptEula

if (Test-Path -LiteralPath $context.DebugPackRoot) {
    if ($PSCmdlet.ShouldProcess($context.DebugPackRoot, 'Remove existing BRNTalk debug datapack')) {
        Remove-Item -LiteralPath $context.DebugPackRoot -Recurse -Force
    }
}

& (Join-Path $PSScriptRoot 'use-dialogue-fixture.ps1') -Type $Type -Fixture $fixtureName

if ($StartServer) {
    if (-not $NoBuild) {
        if ($PSCmdlet.ShouldProcess($context.RepoRoot, 'Run preflight build with --no-configuration-cache')) {
            Invoke-BrntalkGradle -Context $context -Arguments @('build', '--no-configuration-cache')
        }
    } else {
        Write-Host '[BRNTalk RCON Smoke] Skipping preflight build because -NoBuild was supplied.' -ForegroundColor Yellow
    }

    Ensure-BrntalkDirectory -Path $context.LogDir
    $stdoutPath = Join-Path $context.LogDir 'brntalk-debug-server.out.log'
    $stderrPath = Join-Path $context.LogDir 'brntalk-debug-server.err.log'

    if ($PSCmdlet.ShouldProcess($context.RepoRoot, 'Start debug server as a background process')) {
        Repair-BrntalkProcessPathEnvironment
        $serverProcess = Start-Process `
            -FilePath (Join-Path $context.RepoRoot 'gradlew.bat') `
            -ArgumentList @('runServer', '--no-configuration-cache') `
            -WorkingDirectory $context.RepoRoot `
            -WindowStyle Hidden `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath `
            -PassThru

        Write-Host "[BRNTalk RCON Smoke] Started debug server process: $($serverProcess.Id)"
    }
}

Wait-BrntalkRcon -HostName $HostName -Port $RconPort -Password $RconPassword -TimeoutSeconds $ServerWaitSeconds

$logLineCountBeforeReload = if (Test-Path -LiteralPath $context.LatestLogPath) {
    @(Get-Content -LiteralPath $context.LatestLogPath).Count
} else {
    0
}

$commands = @(Get-BrntalkRconSmokeCommands -FixtureName $fixtureName -TargetPlayer $PlayerName)
$commandFailures = New-Object System.Collections.Generic.List[string]

foreach ($command in $commands) {
    Write-Host "[BRNTalk RCON Smoke] > $command" -ForegroundColor Cyan
    $response = Invoke-BrntalkRconCommand -HostName $HostName -Port $RconPort -Password $RconPassword -Command $command
    if (-not [string]::IsNullOrWhiteSpace($response)) {
        Write-Host $response
    }

    if ($response -match 'Unknown or incomplete command|No player was found|Incorrect argument|No such player') {
        $commandFailures.Add("Command reported a failure: $command -> $response")
    }

    Start-Sleep -Seconds $CommandDelaySeconds
}

if ($Type -eq 'valid' -and [string]::IsNullOrWhiteSpace($PlayerName)) {
    Write-Host '[BRNTalk RCON Smoke] No -PlayerName supplied; runtime /brntalk commands were skipped.' -ForegroundColor Yellow
}

if ($fixtureName -eq 'choice_branch' -and -not [string]::IsNullOrWhiteSpace($PlayerName)) {
    Write-Host '[BRNTalk RCON Smoke] Choice selection still requires the client UI; RCON only starts the thread.' -ForegroundColor Yellow
}

if ($commandFailures.Count -gt 0) {
    Write-Host '[BRNTalk RCON Smoke] Verdict: FAIL' -ForegroundColor Red
    $commandFailures | ForEach-Object { Write-Host "[BRNTalk RCON Smoke] - $_" -ForegroundColor Red }
    exit 1
}

$readLogArgs = @{
    Tail = $true
    TailCount = 120
    SummaryOnly = $true
    PassThru = $true
}

if ($Type -eq 'invalid') {
    $readLogArgs.ExpectPattern = @('Validation:', 'Skipping script')
} elseif ($Type -eq 'warning') {
    $readLogArgs.ExpectPattern = @('Validation:', 'WARNING')
    $readLogArgs.RejectPattern = @('Skipping script', 'Failed to load conversation file')
} else {
    $readLogArgs.FailOnValidationError = $true
}

$readLogArgs.SkipFirstLineCount = $logLineCountBeforeReload

try {
    $logResult = & (Join-Path $PSScriptRoot 'read-brntalk-log.ps1') @readLogArgs
} finally {
    if ($StopServer) {
        try {
            Invoke-BrntalkRconCommand -HostName $HostName -Port $RconPort -Password $RconPassword -Command 'stop' | Out-Null
        } catch {
            Write-Host "[BRNTalk RCON Smoke] Could not stop server through RCON: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
}

if ($null -ne $logResult) {
    if ($logResult.Verdict -eq 'FAIL') {
        exit 1
    }
    if ($logResult.Verdict -eq 'UNKNOWN') {
        exit 2
    }
}
