[CmdletBinding(SupportsShouldProcess)]
param(
    [ValidateSet('all', 'invalid', 'warning')]
    [string]$Type = 'all',

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

function New-BrntalkValidationFixtureCase {
    param(
        [Parameter(Mandatory)]
        [ValidateSet('invalid', 'warning')]
        [string]$Type,

        [Parameter(Mandatory)]
        [string]$Fixture,

        [Parameter(Mandatory)]
        [string[]]$ExpectPattern,

        [string[]]$RejectPattern = @()
    )

    [PSCustomObject]@{
        Type = $Type
        Fixture = $Fixture
        ExpectPattern = $ExpectPattern
        RejectPattern = $RejectPattern
    }
}

$cases = @(
    New-BrntalkValidationFixtureCase -Type invalid -Fixture 'empty_messages' -ExpectPattern @('Validation:', 'Script contains no messages', 'Skipping script')
    New-BrntalkValidationFixtureCase -Type invalid -Fixture 'duplicate_message_id' -ExpectPattern @('Validation:', 'Duplicate message id', 'Skipping script')
    New-BrntalkValidationFixtureCase -Type invalid -Fixture 'missing_next_id' -ExpectPattern @('Validation:', 'missing nextId', 'missing_node', 'Skipping script')
    New-BrntalkValidationFixtureCase -Type invalid -Fixture 'missing_choice_next_id' -ExpectPattern @('Validation:', 'broken_choice', 'missing_choice_target', 'Skipping script')
    New-BrntalkValidationFixtureCase -Type invalid -Fixture 'empty_choice_node' -ExpectPattern @('Validation:', 'has no choices', 'Skipping script')
    New-BrntalkValidationFixtureCase -Type invalid -Fixture 'duplicate_choice_id' -ExpectPattern @('Validation:', 'duplicate choice id', 'Skipping script')
    New-BrntalkValidationFixtureCase -Type invalid -Fixture 'infinite_text_loop' -ExpectPattern @('Validation:', 'Infinite TEXT auto-advance loop', 'Skipping script')
    New-BrntalkValidationFixtureCase -Type warning -Fixture 'blank_message_text' -ExpectPattern @('Validation:', 'WARNING', 'blank text') -RejectPattern @('Skipping script', 'Failed to load conversation file')
    New-BrntalkValidationFixtureCase -Type warning -Fixture 'blank_choice_text' -ExpectPattern @('Validation:', 'WARNING', 'blank_choice') -RejectPattern @('Skipping script', 'Failed to load conversation file')
    New-BrntalkValidationFixtureCase -Type warning -Fixture 'wait_without_next' -ExpectPattern @('Validation:', 'WARNING', 'has no nextId') -RejectPattern @('Skipping script', 'Failed to load conversation file')
    New-BrntalkValidationFixtureCase -Type warning -Fixture 'unreachable_message' -ExpectPattern @('Validation:', 'WARNING', 'unreachable') -RejectPattern @('Skipping script', 'Failed to load conversation file')
)

if ($Type -ne 'all') {
    $cases = @($cases | Where-Object { $_.Type -eq $Type })
}

$context = Get-BrntalkDebugContext -ScriptRoot $PSScriptRoot
$serverProcess = $null
$failedCases = New-Object System.Collections.Generic.List[string]

Write-Host "[BRNTalk Validation Fixtures] Cases: $($cases.Count)"
Write-Host "[BRNTalk Validation Fixtures] RCON: ${HostName}:$RconPort"

Enable-BrntalkDebugRcon -Context $context -Password $RconPassword -Port $RconPort -AcceptEula:$AcceptEula

if ($StartServer) {
    if (-not $NoBuild) {
        if ($PSCmdlet.ShouldProcess($context.RepoRoot, 'Run preflight build with --no-configuration-cache')) {
            Invoke-BrntalkGradle -Context $context -Arguments @('build', '--no-configuration-cache')
        }
    } else {
        Write-Host '[BRNTalk Validation Fixtures] Skipping preflight build because -NoBuild was supplied.' -ForegroundColor Yellow
    }

    Ensure-BrntalkDirectory -Path $context.LogDir
    $stdoutPath = Join-Path $context.LogDir 'brntalk-validation-server.out.log'
    $stderrPath = Join-Path $context.LogDir 'brntalk-validation-server.err.log'

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

        Write-Host "[BRNTalk Validation Fixtures] Started debug server process: $($serverProcess.Id)"
    }
}

try {
    Wait-BrntalkRcon -HostName $HostName -Port $RconPort -Password $RconPassword -TimeoutSeconds $ServerWaitSeconds

    foreach ($case in $cases) {
        Write-Host ''
        Write-Host "[BRNTalk Validation Fixtures] Case: $($case.Type)/$($case.Fixture)" -ForegroundColor Cyan

        $logLineCountBeforeReload = if (Test-Path -LiteralPath $context.LatestLogPath) {
            @(Get-Content -LiteralPath $context.LatestLogPath).Count
        } else {
            0
        }

        & (Join-Path $PSScriptRoot 'use-dialogue-fixture.ps1') -Type $case.Type -Fixture $case.Fixture

        Write-Host '[BRNTalk Validation Fixtures] > reload' -ForegroundColor Cyan
        $response = Invoke-BrntalkRconCommand -HostName $HostName -Port $RconPort -Password $RconPassword -Command 'reload'
        if (-not [string]::IsNullOrWhiteSpace($response)) {
            Write-Host $response
        }

        Start-Sleep -Seconds $CommandDelaySeconds

        $readLogArgs = @{
            Tail = $true
            TailCount = 80
            SummaryOnly = $true
            PassThru = $true
            SkipFirstLineCount = $logLineCountBeforeReload
            ExpectPattern = $case.ExpectPattern
        }

        if ($case.RejectPattern.Count -gt 0) {
            $readLogArgs.RejectPattern = $case.RejectPattern
        }

        $result = & (Join-Path $PSScriptRoot 'read-brntalk-log.ps1') @readLogArgs
        if ($null -eq $result -or $result.Verdict -ne 'PASS') {
            $failedCases.Add("$($case.Type)/$($case.Fixture): $($result.Verdict)")
        }
    }
} finally {
    if ($StopServer) {
        try {
            Invoke-BrntalkRconCommand -HostName $HostName -Port $RconPort -Password $RconPassword -Command 'stop' | Out-Null
        } catch {
            Write-Host "[BRNTalk Validation Fixtures] Could not stop server through RCON: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
}

Write-Host ''
if ($failedCases.Count -gt 0) {
    Write-Host '[BRNTalk Validation Fixtures] Verdict: FAIL' -ForegroundColor Red
    $failedCases | ForEach-Object { Write-Host "[BRNTalk Validation Fixtures] - $_" -ForegroundColor Red }
    exit 1
}

Write-Host '[BRNTalk Validation Fixtures] Verdict: PASS' -ForegroundColor Green
