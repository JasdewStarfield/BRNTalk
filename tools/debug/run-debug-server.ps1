[CmdletBinding(SupportsShouldProcess)]
param(
    [switch]$CleanLog,
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'DebugTools.Common.ps1')

$context = Get-BrntalkDebugContext -ScriptRoot $PSScriptRoot

Write-Host '[BRNTalk Debug] Repo-only Phase 1 workflow. No mod version bump is needed.' -ForegroundColor Cyan
Write-Host "[BRNTalk Debug] Repo root: $($context.RepoRoot)"
Write-Host "[BRNTalk Debug] Latest log: $($context.LatestLogPath)"
Write-Host "[BRNTalk Debug] Datapacks: $($context.DatapacksDir)"
Write-Host "[BRNTalk Debug] Server config: $($context.ServerConfigDir)"

Ensure-BrntalkDirectory -Path $context.LogDir
Ensure-BrntalkDirectory -Path $context.DatapacksDir

if ($CleanLog -and (Test-Path -LiteralPath $context.LatestLogPath)) {
    if ($PSCmdlet.ShouldProcess($context.LatestLogPath, 'Clear latest.log before starting the debug server')) {
        Clear-Content -LiteralPath $context.LatestLogPath
    }
}

if (-not $NoBuild) {
    if ($PSCmdlet.ShouldProcess($context.RepoRoot, 'Run preflight build with --no-configuration-cache')) {
        Invoke-BrntalkGradle -Context $context -Arguments @('build', '--no-configuration-cache')
    }
} else {
    Write-Host '[BRNTalk Debug] Skipping the preflight build because -NoBuild was supplied.' -ForegroundColor Yellow
}

if ($PSCmdlet.ShouldProcess($context.RepoRoot, 'Start the NeoForge debug server with --no-configuration-cache')) {
    Invoke-BrntalkGradle -Context $context -Arguments @('runServer', '--no-configuration-cache')
}
