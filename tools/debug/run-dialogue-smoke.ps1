[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)]
    [ValidateSet('invalid', 'valid')]
    [string]$Type,

    [Parameter(Mandatory)]
    [string]$Fixture,

    [switch]$StartServer,
    [switch]$NoBuild,
    [string[]]$ExpectPattern = @(),
    [string[]]$RejectPattern = @(),
    [switch]$FailOnValidationError
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'DebugTools.Common.ps1')

function Get-BrntalkSmokeCommands {
    param(
        [Parameter(Mandatory)]
        [string]$FixtureName
    )

    switch ($FixtureName) {
        'basic_linear' {
            return @('/reload', '/brntalk start brntalk_debug:basic_linear')
        }
        'choice_branch' {
            return @('/reload', '/brntalk start brntalk_debug:choice_branch', 'Choose either displayed option in the BRNTalk UI.')
        }
        'wait_resume' {
            return @('/reload', '/brntalk start brntalk_debug:wait_resume', '/brntalk resume @s brntalk_debug:wait_resume wait_node')
        }
        'action_command' {
            return @('/reload', '/brntalk start brntalk_debug:action_command')
        }
        'multi_thread' {
            return @('/reload', '/brntalk start brntalk_debug:multi_thread_alpha', '/brntalk start brntalk_debug:multi_thread_beta')
        }
        default {
            return @('/reload')
        }
    }
}

$context = Get-BrntalkDebugContext -ScriptRoot $PSScriptRoot
$fixtureName = [System.IO.Path]::GetFileNameWithoutExtension($Fixture)

Write-Host "[BRNTalk Smoke] Fixture: $Type/$fixtureName"
Write-Host '[BRNTalk Smoke] Clearing and reinstalling the debug datapack fixture.'

if (Test-Path -LiteralPath $context.DebugPackRoot) {
    if ($PSCmdlet.ShouldProcess($context.DebugPackRoot, 'Remove existing BRNTalk debug datapack')) {
        Remove-Item -LiteralPath $context.DebugPackRoot -Recurse -Force
    }
}

& (Join-Path $PSScriptRoot 'use-dialogue-fixture.ps1') -Type $Type -Fixture $fixtureName

Write-Host ''
Write-Host '[BRNTalk Smoke] Manual in-game steps:' -ForegroundColor Cyan
Get-BrntalkSmokeCommands -FixtureName $fixtureName |
    ForEach-Object { Write-Host "  $_" }

if ($StartServer) {
    if (-not $NoBuild) {
        if ($PSCmdlet.ShouldProcess($context.RepoRoot, 'Run preflight build with --no-configuration-cache')) {
            Invoke-BrntalkGradle -Context $context -Arguments @('build', '--no-configuration-cache')
        }
    } else {
        Write-Host '[BRNTalk Smoke] Skipping preflight build because -NoBuild was supplied.' -ForegroundColor Yellow
    }

    if ($PSCmdlet.ShouldProcess($context.RepoRoot, 'Start the NeoForge debug server with --no-configuration-cache')) {
        Invoke-BrntalkGradle -Context $context -Arguments @('runServer', '--no-configuration-cache')
    }
}

if (-not (Test-Path -LiteralPath $context.LatestLogPath)) {
    Write-Host "[BRNTalk Smoke] Verdict: UNKNOWN - log does not exist yet: $($context.LatestLogPath)" -ForegroundColor Yellow
    return
}

if ($Type -eq 'invalid' -and $ExpectPattern.Count -eq 0) {
    $ExpectPattern = @('Validation:', 'Skipping script')
}

if ($Type -eq 'valid' -and -not $FailOnValidationError) {
    $FailOnValidationError = $true
}

Write-Host ''
$readLogArgs = @{
    Tail = $true
    TailCount = 80
    SummaryOnly = $true
}

if ($ExpectPattern.Count -gt 0) {
    $readLogArgs.ExpectPattern = $ExpectPattern
}

if ($RejectPattern.Count -gt 0) {
    $readLogArgs.RejectPattern = $RejectPattern
}

if ($FailOnValidationError) {
    $readLogArgs.FailOnValidationError = $true
}

& (Join-Path $PSScriptRoot 'read-brntalk-log.ps1') @readLogArgs
