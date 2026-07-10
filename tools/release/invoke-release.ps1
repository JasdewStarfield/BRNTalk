[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'Medium')]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$')]
    [string]$Version,

    [datetime]$ReleaseDate = (Get-Date),

    [string]$NeoForgeWorktree = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,

    [string]$ForgeWorktree = (Join-Path (Split-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path -Parent) 'BRNTalk-1.20.1-forge'),

    [ValidateSet('release', 'beta', 'alpha')]
    [string]$ReleaseType = 'release',

    [switch]$Prepare,

    [switch]$ConfirmReadmeReviewed,

    [switch]$CommitAndPush,

    [switch]$Dispatch,

    [switch]$PublishModrinth,

    [switch]$PublishCurseForge,

    [switch]$DraftGithubRelease,

    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ReleaseTools.Common.ps1')

function Get-BrntalkGithubCli {
    $command = Get-Command gh -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    # GitHub CLI is installed outside PATH on some Windows setups.
    $windowsPath = 'C:\Program Files\GitHub CLI\gh.exe'
    if (Test-Path -LiteralPath $windowsPath -PathType Leaf) {
        return $windowsPath
    }

    throw 'GitHub CLI was not found. Install gh or add it to PATH before using -Dispatch.'
}

$NeoForgeWorktree = (Resolve-Path -LiteralPath $NeoForgeWorktree).Path
$ForgeWorktree = (Resolve-Path -LiteralPath $ForgeWorktree).Path
$dateText = $ReleaseDate.ToString('yyyy-MM-dd')
$cnUnreleasedHeading = -join [char[]](26410, 21457, 24067)
$cnAddedHeading = -join [char[]](26032, 22686)
$cnNothing = -join [char[]](26242, 26080, 12290)
$releaseFiles = @(
    'gradle.properties',
    'CHANGELOG_cn.md',
    'CHANGELOG_en.md',
    'README.md',
    'README_en.md'
)
$targets = @(
    [PSCustomObject]@{
        Name       = 'NeoForge 1.21.1'
        Root       = $NeoForgeWorktree
        Branch     = 'mc/1.21.1-neoforge'
        Minecraft  = '1.21.1'
        Loader     = 'NeoForge'
        Java       = '21'
    },
    [PSCustomObject]@{
        Name       = 'Forge 1.20.1'
        Root       = $ForgeWorktree
        Branch     = 'mc/1.20.1-forge'
        Minecraft  = '1.20.1'
        Loader     = 'Forge'
        Java       = '17'
    }
)

foreach ($target in $targets) {
    $actualBranch = (& git -C $target.Root branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or $actualBranch -ne $target.Branch) {
        throw "$($target.Name) must be on branch '$($target.Branch)', found '$actualBranch'."
    }

    # Release preparation only owns metadata files; feature/fix code must already be committed.
    Assert-BrntalkNoUnexpectedChanges -ProjectRoot $target.Root -AllowedPaths $releaseFiles
}

$previousTag = Get-BrntalkLatestVersionTag -ProjectRoot $NeoForgeWorktree -ExcludeVersion $Version
if ($previousTag -eq $Version) {
    throw "Version $Version is identical to the previous release tag."
}

$existingTag = & git -C $NeoForgeWorktree tag --list $Version
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect existing release tags.'
}
if ($existingTag -and -not $Dispatch) {
    throw "Tag $Version already exists. Refusing to prepare a duplicate release."
}

if ($Prepare) {
    $neoCn = Get-BrntalkChangelogSection -Path (Join-Path $NeoForgeWorktree 'CHANGELOG_cn.md') -Heading $cnUnreleasedHeading
    $forgeCn = Get-BrntalkChangelogSection -Path (Join-Path $ForgeWorktree 'CHANGELOG_cn.md') -Heading $cnUnreleasedHeading
    $neoEn = Get-BrntalkChangelogSection -Path (Join-Path $NeoForgeWorktree 'CHANGELOG_en.md') -Heading 'Unreleased'
    $forgeEn = Get-BrntalkChangelogSection -Path (Join-Path $ForgeWorktree 'CHANGELOG_en.md') -Heading 'Unreleased'
    if ($neoCn -ne $forgeCn -or $neoEn -ne $forgeEn) {
        throw 'The two worktrees have different Unreleased changelog contents. Sync them before preparing the release.'
    }

    foreach ($target in $targets) {
        if ($PSCmdlet.ShouldProcess($target.Root, "Prepare BRNTalk $Version release metadata")) {
            Set-BrntalkProperty -ProjectRoot $target.Root -Name 'mod_version' -Value $Version
            $cnEmptySection = "### $cnAddedHeading{0}{0}- $cnNothing" -f [Environment]::NewLine
            Move-BrntalkUnreleasedSection -Path (Join-Path $target.Root 'CHANGELOG_cn.md') -UnreleasedHeading $cnUnreleasedHeading -EmptySection $cnEmptySection -Version $Version -ReleaseDate $dateText
            Move-BrntalkUnreleasedSection -Path (Join-Path $target.Root 'CHANGELOG_en.md') -UnreleasedHeading 'Unreleased' -EmptySection ("### Added{0}{0}- Nothing yet." -f [Environment]::NewLine) -Version $Version -ReleaseDate $dateText
        }
    }
}

foreach ($target in $targets) {
    $tag = Get-BrntalkLatestVersionTag -ProjectRoot $target.Root -ExcludeVersion $Version
    $readmeChanged = Test-BrntalkReadmeChangedSinceTag -ProjectRoot $target.Root -Tag $tag
    if (-not $readmeChanged -and -not $ConfirmReadmeReviewed) {
        throw "$($target.Name) bilingual READMEs have not changed since tag '$tag'. Review them, then rerun with -ConfirmReadmeReviewed if no edit is needed."
    }

    $testArguments = @{
        ProjectRoot     = $target.Root
        Version         = $Version
        MinecraftVersion = $target.Minecraft
        Loader          = $target.Loader
        JavaVersion     = $target.Java
        SkipBuild       = $SkipBuild
    }
    & (Join-Path $PSScriptRoot 'test-release.ps1') @testArguments
}

$metadataOutputRoot = Join-Path $NeoForgeWorktree 'build\release-metadata'
& (Join-Path $PSScriptRoot 'render-platform-metadata.ps1') -ProjectRoot $NeoForgeWorktree -Version $Version -Target neoforge_1_21_1 -ReleaseType $ReleaseType -OutputPath (Join-Path $metadataOutputRoot 'neoforge_1_21_1.json')
& (Join-Path $PSScriptRoot 'render-platform-metadata.ps1') -ProjectRoot $NeoForgeWorktree -Version $Version -Target forge_1_20_1 -ReleaseType $ReleaseType -OutputPath (Join-Path $metadataOutputRoot 'forge_1_20_1.json')

if ($CommitAndPush) {
    foreach ($target in $targets) {
        if (-not $PSCmdlet.ShouldProcess($target.Branch, "Commit and push BRNTalk $Version release metadata")) {
            continue
        }

        & git -C $target.Root add -- @releaseFiles
        if ($LASTEXITCODE -ne 0) {
            throw "git add failed in $($target.Root)."
        }

        & git -C $target.Root diff --cached --quiet
        if ($LASTEXITCODE -eq 1) {
            & git -C $target.Root commit -m "Prepare $Version release"
            if ($LASTEXITCODE -ne 0) {
                throw "git commit failed in $($target.Root)."
            }
        }

        & git -C $target.Root push origin $target.Branch
        if ($LASTEXITCODE -ne 0) {
            throw "git push failed for $($target.Branch)."
        }
    }
}

if ($Dispatch) {
    foreach ($target in $targets) {
        $localHead = (& git -C $target.Root rev-parse HEAD).Trim()
        $upstreamHead = (& git -C $target.Root rev-parse '@{upstream}').Trim()
        if ($localHead -ne $upstreamHead) {
            throw "$($target.Branch) has commits that are not pushed; refusing to dispatch the release workflow."
        }
    }

    $inputs = @(
        'workflow', 'run', 'release.yml',
        '--ref', 'mc/1.21.1-neoforge',
        '-f', "version=$Version",
        '-f', "release_type=$ReleaseType",
        '-f', "draft=$($DraftGithubRelease.ToString().ToLowerInvariant())",
        '-f', "publish_modrinth=$($PublishModrinth.ToString().ToLowerInvariant())",
        '-f', "publish_curseforge=$($PublishCurseForge.ToString().ToLowerInvariant())"
    )

    if ($PSCmdlet.ShouldProcess('GitHub Actions release.yml', "Dispatch BRNTalk $Version publication")) {
        $githubCli = Get-BrntalkGithubCli
        & $githubCli @inputs
        if ($LASTEXITCODE -ne 0) {
            throw 'Failed to dispatch the GitHub release workflow.'
        }
    }
}

Write-Host "[BRNTalk Release] Local release gates completed for $Version." -ForegroundColor Green
