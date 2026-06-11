[CmdletBinding(SupportsShouldProcess)]
param()

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'DebugTools.Common.ps1')

$context = Get-BrntalkDebugContext -ScriptRoot $PSScriptRoot

if (-not (Test-Path -LiteralPath $context.DebugPackRoot)) {
    Write-Host '[BRNTalk Debug] The debug datapack is already absent. Nothing to clear.' -ForegroundColor Yellow
    return
}

if ($PSCmdlet.ShouldProcess($context.DebugPackRoot, 'Remove the BRNTalk debug datapack')) {
    Remove-Item -LiteralPath $context.DebugPackRoot -Recurse -Force
}

Write-Host '[BRNTalk Debug] Cleared the BRNTalk debug datapack.' -ForegroundColor Green
