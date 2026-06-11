[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Fixture,
    [string]$SourcePath
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'DebugTools.Common.ps1')

$context = Get-BrntalkDebugContext -ScriptRoot $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($Fixture) -and [string]::IsNullOrWhiteSpace($SourcePath)) {
    throw 'Provide either -Fixture <name> or -SourcePath <path>.'
}

if (-not [string]::IsNullOrWhiteSpace($Fixture) -and -not [string]::IsNullOrWhiteSpace($SourcePath)) {
    throw 'Use either -Fixture or -SourcePath, not both at the same time.'
}

if (-not [string]::IsNullOrWhiteSpace($Fixture)) {
    $fixtureFileName = [System.IO.Path]::GetFileNameWithoutExtension($Fixture) + '.json'
    $source = Join-Path $context.FixturesDir $fixtureFileName
} else {
    $source = (Resolve-Path -LiteralPath $SourcePath).Path
    $fixtureFileName = [System.IO.Path]::GetFileName($source)
}

if (-not (Test-Path -LiteralPath $source)) {
    throw "The source dialogue file does not exist: $source"
}

Ensure-BrntalkDebugDatapack -Context $context

if ($PSCmdlet.ShouldProcess($context.DebugDialogueDir, 'Clear previous debug dialogue files')) {
    Clear-BrntalkDebugDialogues -Context $context
}

$target = Join-Path $context.DebugDialogueDir $fixtureFileName

if ($PSCmdlet.ShouldProcess($target, "Install debug dialogue from '$source'")) {
    Copy-Item -LiteralPath $source -Destination $target -Force
}

Write-Host "[BRNTalk Debug] Installed debug dialogue: $target" -ForegroundColor Green
Write-Host '[BRNTalk Debug] Next step: run /reload or restart the debug server.' -ForegroundColor Cyan
