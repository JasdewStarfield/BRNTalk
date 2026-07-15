Set-StrictMode -Version Latest

function Set-BrntalkUtf8File {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [AllowEmptyString()]
        [string]$Content
    )

    # Use UTF-8 without BOM consistently in Windows PowerShell 5 and PowerShell 7.
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8)
}

function Get-BrntalkProperty {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$Name
    )

    $propertiesPath = Join-Path $ProjectRoot 'gradle.properties'
    $matchingLine = Get-Content -LiteralPath $propertiesPath -Encoding UTF8 |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=(.*)$" } |
        Select-Object -First 1

    if ($null -eq $matchingLine) {
        throw "Property '$Name' was not found in $propertiesPath."
    }

    return ($matchingLine -split '=', 2)[1].Trim()
}

function Set-BrntalkProperty {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$Name,

        [Parameter(Mandatory)]
        [string]$Value
    )

    $propertiesPath = Join-Path $ProjectRoot 'gradle.properties'
    $content = Get-Content -LiteralPath $propertiesPath -Raw -Encoding UTF8
    $pattern = "(?m)^$([regex]::Escape($Name))=.*$"
    if ($content -notmatch $pattern) {
        throw "Property '$Name' was not found in $propertiesPath."
    }

    # A narrow regex replacement avoids rewriting unrelated UTF-8 comments in gradle.properties.
    $regex = [regex]::new($pattern)
    $updated = $regex.Replace($content, "$Name=$Value", 1)
    Set-BrntalkUtf8File -Path $propertiesPath -Content $updated
}

function Get-BrntalkChangelogSection {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [string]$Heading
    )

    $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    $escapedHeading = [regex]::Escape($Heading)
    $match = [regex]::Match(
        $content,
        "(?ms)^## \[$escapedHeading\][^\r\n]*\r?\n(?<body>.*?)(?=^## \[|\z)"
    )

    if (-not $match.Success) {
        throw "Section '## [$Heading]' was not found in $Path."
    }

    return $match.Groups['body'].Value.Trim()
}

function Test-BrntalkUnreleasedPlaceholder {
    param(
        [Parameter(Mandatory)]
        [string]$Body
    )

    # Keep the script ASCII-only so Windows PowerShell 5 can parse it without relying on a UTF-8 BOM.
    $cnNothing = '- ' + (-join [char[]](26242, 26080, 12290))
    $meaningfulLines = $Body -split '\r?\n' |
        ForEach-Object { $_.Trim() } |
        Where-Object {
            $_ -and
            $_ -notmatch '^### ' -and
            $_ -notin @('- Nothing yet.', $cnNothing)
        }

    return @($meaningfulLines).Count -eq 0
}

function Move-BrntalkUnreleasedSection {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [string]$UnreleasedHeading,

        [Parameter(Mandatory)]
        [string]$EmptySection,

        [Parameter(Mandatory)]
        [string]$Version,

        [Parameter(Mandatory)]
        [string]$ReleaseDate
    )

    $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    $escapedHeading = [regex]::Escape($UnreleasedHeading)
    $match = [regex]::Match(
        $content,
        "(?ms)^## \[$escapedHeading\][^\r\n]*\r?\n(?<body>.*?)(?=^## \[|\z)"
    )

    if (-not $match.Success) {
        throw "Section '## [$UnreleasedHeading]' was not found in $Path."
    }

    $body = $match.Groups['body'].Value.Trim()
    if (Test-BrntalkUnreleasedPlaceholder -Body $body) {
        throw "The Unreleased section in $Path has no release notes to roll forward."
    }

    $newline = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    # Normalize caller-provided placeholder sections to the target file's newline style.
    $emptySectionBody = $EmptySection.Trim() -replace "\r\n|\n|\r", $newline
    $replacement = @(
        "## [$UnreleasedHeading]",
        '',
        $emptySectionBody,
        '',
        "## [$Version] - $ReleaseDate",
        '',
        $body,
        ''
    ) -join $newline

    $updated = $content.Remove($match.Index, $match.Length).Insert($match.Index, $replacement)
    Set-BrntalkUtf8File -Path $Path -Content $updated
}

function Get-BrntalkLatestVersionTag {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [string]$ExcludeVersion
    )

    $tags = & git -C $ProjectRoot tag --merged HEAD --sort=-version:refname
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to list Git tags in $ProjectRoot."
    }

    return $tags |
        Where-Object { $_ -match '^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$' -and $_ -ne $ExcludeVersion } |
        Select-Object -First 1
}

function Test-BrntalkReadmeChangedSinceTag {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [string]$Tag
    )

    $status = & git -C $ProjectRoot status --porcelain=v1 -- README.md README_en.md
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect README status in $ProjectRoot."
    }
    if (@($status).Count -gt 0) {
        return $true
    }

    if (-not $Tag) {
        return $true
    }

    & git -C $ProjectRoot diff --quiet "$Tag..HEAD" -- README.md README_en.md
    return $LASTEXITCODE -eq 1
}

function Assert-BrntalkReadmes {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$MinecraftVersion,

        [Parameter(Mandatory)]
        [string]$Loader,

        [Parameter(Mandatory)]
        [string]$JavaVersion
    )

    foreach ($fileName in @('README.md', 'README_en.md')) {
        $path = Join-Path $ProjectRoot $fileName
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing bilingual README file: $path"
        }

        $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        foreach ($requiredText in @($MinecraftVersion, $Loader, "Java", $JavaVersion)) {
            if (-not $content.Contains($requiredText)) {
                throw "$fileName in $ProjectRoot does not mention required release value '$requiredText'."
            }
        }
    }
}

function Assert-BrntalkChangelogVersion {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$Version
    )

    foreach ($fileName in @('CHANGELOG_cn.md', 'CHANGELOG_en.md')) {
        $path = Join-Path $ProjectRoot $fileName
        [void](Get-BrntalkChangelogSection -Path $path -Heading $Version)
    }
}

function Assert-BrntalkNoUnexpectedChanges {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [string[]]$AllowedPaths = @()
    )

    $statusLines = & git -C $ProjectRoot status --porcelain=v1
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect Git status in $ProjectRoot."
    }

    $unexpected = foreach ($line in $statusLines) {
        if ($line.Length -lt 4) {
            continue
        }

        $path = $line.Substring(3).Replace('\\', '/')
        if ($path.Contains(' -> ')) {
            $path = ($path -split ' -> ', 2)[1]
        }
        if ($path -notin $AllowedPaths) {
            $line
        }
    }

    if (@($unexpected).Count -gt 0) {
        throw "Unexpected working-tree changes in ${ProjectRoot}:`n$($unexpected -join "`n")"
    }
}

function Invoke-BrntalkBuild {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot
    )

    Push-Location $ProjectRoot
    try {
        # $IsWindows is unavailable in Windows PowerShell 5; $env:OS works there and in pwsh.
        $wrapper = if ($env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }
        & $wrapper build --no-configuration-cache
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed in $ProjectRoot with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Assert-BrntalkDiffCheck {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot
    )

    & git -C $ProjectRoot diff --check
    if ($LASTEXITCODE -ne 0) {
        throw "git diff --check failed in $ProjectRoot."
    }
}

function Get-BrntalkReleaseNotes {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$Version
    )

    $cn = Get-BrntalkChangelogSection -Path (Join-Path $ProjectRoot 'CHANGELOG_cn.md') -Heading $Version
    $en = Get-BrntalkChangelogSection -Path (Join-Path $ProjectRoot 'CHANGELOG_en.md') -Heading $Version
    $cnLabel = -join [char[]](20013, 25991)
    return "## $cnLabel`n`n$cn`n`n## English`n`n$en`n"
}
