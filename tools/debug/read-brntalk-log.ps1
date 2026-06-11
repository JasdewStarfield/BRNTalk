[CmdletBinding()]
param(
    [switch]$Tail,
    [int]$TailCount = 40,
    [datetime]$Since,
    [switch]$SummaryOnly
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'DebugTools.Common.ps1')

function Get-BrntalkLogTimestamp {
    param(
        [Parameter(Mandatory)]
        [string]$Line
    )

    # The local debug log currently formats timestamps like:
    # [091月2026 06:16:18.517] ...
    if ($Line -match '^\[(?<day>\d{2})(?<month>\d{1,2})月(?<year>\d{4}) (?<time>\d{2}:\d{2}:\d{2}\.\d{3})\]') {
        $stamp = '{0:D4}-{1:D2}-{2:D2} {3}' -f [int]$matches.year, [int]$matches.month, [int]$matches.day, $matches.time
        return [datetime]::ParseExact($stamp, 'yyyy-MM-dd HH:mm:ss.fff', [System.Globalization.CultureInfo]::InvariantCulture)
    }

    return $null
}

$context = Get-BrntalkDebugContext -ScriptRoot $PSScriptRoot

if (-not (Test-Path -LiteralPath $context.LatestLogPath)) {
    throw "The debug log does not exist yet: $($context.LatestLogPath)"
}

$patterns = @(
    '\[BRNTalk\]',
    'Validation:',
    'Skipping script',
    'Failed to load conversation file'
)

$lines = Get-Content -LiteralPath $context.LatestLogPath
$matchingLines = foreach ($line in $lines) {
    $isInteresting = $false
    foreach ($pattern in $patterns) {
        if ($line -match $pattern) {
            $isInteresting = $true
            break
        }
    }

    if (-not $isInteresting) {
        continue
    }

    if ($PSBoundParameters.ContainsKey('Since')) {
        $lineTimestamp = Get-BrntalkLogTimestamp -Line $line
        if ($null -eq $lineTimestamp -or $lineTimestamp -lt $Since) {
            continue
        }
    }

    $line
}

if ($Tail) {
    $matchingLines = $matchingLines | Select-Object -Last $TailCount
}

$summary = [PSCustomObject]@{
    LogPath             = $context.LatestLogPath
    MatchingLineCount   = @($matchingLines).Count
    BrntalkLineCount    = @($matchingLines | Where-Object { $_ -match '\[BRNTalk\]' }).Count
    ValidationLineCount = @($matchingLines | Where-Object { $_ -match 'Validation:' }).Count
    SkippedScriptCount  = @($matchingLines | Where-Object { $_ -match 'Skipping script' }).Count
    FileFailureCount    = @($matchingLines | Where-Object { $_ -match 'Failed to load conversation file' }).Count
}

Write-Host "[BRNTalk Debug] Log path: $($summary.LogPath)"
Write-Host "[BRNTalk Debug] Matching lines: $($summary.MatchingLineCount)"
Write-Host "[BRNTalk Debug] BRNTalk lines: $($summary.BrntalkLineCount)"
Write-Host "[BRNTalk Debug] Validation lines: $($summary.ValidationLineCount)"
Write-Host "[BRNTalk Debug] Skipped scripts: $($summary.SkippedScriptCount)"
Write-Host "[BRNTalk Debug] File load failures: $($summary.FileFailureCount)"

if (-not $SummaryOnly -and @($matchingLines).Count -gt 0) {
    Write-Host ''
    $matchingLines | ForEach-Object { Write-Output $_ }
}
