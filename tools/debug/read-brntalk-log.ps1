[CmdletBinding()]
param(
    [switch]$Tail,
    [int]$TailCount = 40,
    [datetime]$Since,
    [int]$SkipFirstLineCount = 0,
    [switch]$SummaryOnly,
    [string[]]$ExpectPattern = @(),
    [string[]]$RejectPattern = @(),
    [switch]$FailOnValidationError,
    [switch]$ExitWithCode,
    [switch]$PassThru
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
$patterns += $ExpectPattern
$patterns += $RejectPattern

$lines = @(Get-Content -LiteralPath $context.LatestLogPath)
if ($SkipFirstLineCount -gt 0) {
    $lines = @($lines | Select-Object -Skip $SkipFirstLineCount)
}
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

$hasJudgementInput = $ExpectPattern.Count -gt 0 -or $RejectPattern.Count -gt 0 -or $FailOnValidationError
$result = $null
if ($hasJudgementInput) {
    $failedReasons = New-Object System.Collections.Generic.List[string]
    $unknownReasons = New-Object System.Collections.Generic.List[string]
    $verdict = 'PASS'

    foreach ($pattern in $ExpectPattern) {
        $matchCount = @($matchingLines | Where-Object { $_ -match $pattern }).Count
        if ($matchCount -eq 0) {
            $unknownReasons.Add("Expected pattern was not found: $pattern")
        }
    }

    foreach ($pattern in $RejectPattern) {
        $matchCount = @($matchingLines | Where-Object { $_ -match $pattern }).Count
        if ($matchCount -gt 0) {
            $failedReasons.Add("Rejected pattern was found: $pattern")
        }
    }

    if ($FailOnValidationError -and ($summary.ValidationLineCount -gt 0 -or $summary.SkippedScriptCount -gt 0 -or $summary.FileFailureCount -gt 0)) {
        $failedReasons.Add('Validation, skipped script, or file load failure lines were found.')
    }

    if ($failedReasons.Count -gt 0) {
        $verdict = 'FAIL'
        Write-Host '[BRNTalk Debug] Verdict: FAIL' -ForegroundColor Red
        $failedReasons | ForEach-Object { Write-Host "[BRNTalk Debug] - $_" -ForegroundColor Red }
    } elseif ($unknownReasons.Count -gt 0) {
        $verdict = 'UNKNOWN'
        Write-Host '[BRNTalk Debug] Verdict: UNKNOWN' -ForegroundColor Yellow
        $unknownReasons | ForEach-Object { Write-Host "[BRNTalk Debug] - $_" -ForegroundColor Yellow }
    } else {
        Write-Host '[BRNTalk Debug] Verdict: PASS' -ForegroundColor Green
    }

    if ($ExitWithCode) {
        if ($verdict -eq 'FAIL') {
            exit 1
        }
        if ($verdict -eq 'UNKNOWN') {
            exit 2
        }
    }

    $result = [PSCustomObject]@{
        Verdict = $verdict
        FailedReasons = @($failedReasons)
        UnknownReasons = @($unknownReasons)
        Summary = $summary
    }
} elseif ($PassThru) {
    $result = [PSCustomObject]@{
        Verdict = 'NONE'
        FailedReasons = @()
        UnknownReasons = @()
        Summary = $summary
    }
}

if (-not $SummaryOnly -and @($matchingLines).Count -gt 0) {
    Write-Host ''
    $matchingLines | ForEach-Object { Write-Output $_ }
}

if ($PassThru -and $null -ne $result) {
    Write-Output $result
}
