[CmdletBinding(SupportsShouldProcess)]
param(
    [ValidateSet('invalid', 'valid')]
    [string]$Type = 'invalid',
    [string]$Fixture,
    [string]$SourcePath,
    [switch]$List
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'DebugTools.Common.ps1')

$context = Get-BrntalkDebugContext -ScriptRoot $PSScriptRoot

if ($List) {
    $fixtures = @(Get-BrntalkFixtureFiles -Context $context -Type 'all')
    if ($fixtures.Count -eq 0) {
        Write-Host "[BRNTalk Debug] No dialogue fixtures found under $($context.FixtureRoot)." -ForegroundColor Yellow
        return
    }

    $fixtures |
        Format-Table -AutoSize Type, Name, Path
    return
}

if ([string]::IsNullOrWhiteSpace($Fixture) -and [string]::IsNullOrWhiteSpace($SourcePath)) {
    throw 'Provide -Fixture <name>, -SourcePath <path>, or -List.'
}

if (-not [string]::IsNullOrWhiteSpace($Fixture) -and -not [string]::IsNullOrWhiteSpace($SourcePath)) {
    throw 'Use either -Fixture or -SourcePath, not both at the same time.'
}

if (-not [string]::IsNullOrWhiteSpace($Fixture)) {
    $fixtureFileName = [System.IO.Path]::GetFileNameWithoutExtension($Fixture) + '.json'
    $fixtureDirectory = Get-BrntalkFixtureDirectory -Context $context -Type $Type
    $source = Join-Path $fixtureDirectory $fixtureFileName
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
Write-Host "[BRNTalk Debug] Fixture type: $Type"
Write-Host '[BRNTalk Debug] Next step: run /reload or restart the debug server.' -ForegroundColor Cyan
