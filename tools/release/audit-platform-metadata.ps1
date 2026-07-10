[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ModrinthProjectId,

    [Parameter(Mandatory)]
    [string]$CurseForgeProjectId,

    [Parameter(Mandatory)]
    [string]$CurseForgeToken,

    [string]$CurseForgeApiKey,

    [Parameter(Mandatory)]
    [string]$OutputPath,

    [ValidateRange(1, 50)]
    [int]$VersionLimit = 20
)

$ErrorActionPreference = 'Stop'

function Select-ModrinthVersionMetadata {
    param([Parameter(Mandatory)]$Version)

    return [ordered]@{
        id             = $Version.id
        name           = $Version.name
        version_number = $Version.version_number
        version_type   = $Version.version_type
        status         = $Version.status
        featured       = $Version.featured
        game_versions  = @($Version.game_versions)
        loaders        = @($Version.loaders)
        changelog      = $Version.changelog
        dependencies   = @($Version.dependencies)
        files          = @($Version.files | ForEach-Object {
            [ordered]@{
                filename  = $_.filename
                primary   = $_.primary
                file_type = $_.file_type
                size      = $_.size
            }
        })
        date_published = $Version.date_published
        downloads      = $Version.downloads
    }
}

function Select-CurseForgeFileMetadata {
    param(
        [Parameter(Mandatory)]$File,
        [AllowNull()]$Changelog
    )

    return [ordered]@{
        id                     = $File.id
        displayName            = $File.displayName
        fileName               = $File.fileName
        releaseType            = $File.releaseType
        fileStatus             = $File.fileStatus
        gameVersions           = @($File.gameVersions)
        sortableGameVersions   = @($File.sortableGameVersions)
        dependencies           = @($File.dependencies)
        parentProjectFileId    = $File.parentProjectFileId
        exposeAsAlternative    = $File.exposeAsAlternative
        isServerPack           = $File.isServerPack
        isEarlyAccessContent   = $File.isEarlyAccessContent
        fileDate               = $File.fileDate
        downloadCount          = $File.downloadCount
        fileLength             = $File.fileLength
        changelog              = $Changelog
    }
}

$modrinthHeaders = @{
    'User-Agent' = 'JasdewStarfield/BRNTalk-release-metadata-audit (github.com/JasdewStarfield/BRNTalk)'
}
$modrinthProject = Invoke-RestMethod -Method Get -Uri "https://api.modrinth.com/v2/project/$ModrinthProjectId" -Headers $modrinthHeaders
$modrinthVersions = @(
    Invoke-RestMethod -Method Get -Uri "https://api.modrinth.com/v2/project/$ModrinthProjectId/version" -Headers $modrinthHeaders
) | Select-Object -First $VersionLimit

$curseForgeAuthorHeaders = @{
    'Accept'      = 'application/json'
    'X-Api-Token' = $CurseForgeToken
}
$curseForgeAuthorApiAvailable = $false
$curseForgeAuthorApiError = $null
$curseForgeGameVersions = @()
$curseForgeDependencyTypes = @()
try {
    $curseForgeGameVersions = @(
        Invoke-RestMethod -Method Get -Uri 'https://minecraft.curseforge.com/api/game/versions' -Headers $curseForgeAuthorHeaders
    )
    $curseForgeDependencyTypes = @(
        Invoke-RestMethod -Method Get -Uri 'https://minecraft.curseforge.com/api/game/dependencies' -Headers $curseForgeAuthorHeaders
    )
    $curseForgeAuthorApiAvailable = $true
} catch {
    # CurseForge still documents these routes, but the Minecraft host currently returns its HTML not-found page.
    $curseForgeAuthorApiError = $_.Exception.Message
}

$curseForgeProject = $null
$curseForgeFileMetadata = @()
$curseForgePublicApiAvailable = $false
if ($CurseForgeApiKey) {
    $curseForgePublicHeaders = @{
        'Accept'    = 'application/json'
        'x-api-key' = $CurseForgeApiKey
    }
    $curseForgeProjectResponse = Invoke-RestMethod -Method Get -Uri "https://api.curseforge.com/v1/mods/$CurseForgeProjectId" -Headers $curseForgePublicHeaders
    $curseForgeFilesResponse = Invoke-RestMethod -Method Get -Uri "https://api.curseforge.com/v1/mods/$CurseForgeProjectId/files?pageSize=$VersionLimit" -Headers $curseForgePublicHeaders
    $curseForgeProject = $curseForgeProjectResponse.data
    $curseForgeFiles = @($curseForgeFilesResponse.data)
    $curseForgePublicApiAvailable = $true

    $curseForgeFileMetadata = foreach ($file in $curseForgeFiles) {
        $changelogResponse = Invoke-RestMethod -Method Get -Uri "https://api.curseforge.com/v1/mods/$CurseForgeProjectId/files/$($file.id)/changelog" -Headers $curseForgePublicHeaders
        Select-CurseForgeFileMetadata -File $file -Changelog $changelogResponse.data
    }
}

$result = [ordered]@{
    audited_at = (Get-Date).ToUniversalTime().ToString('o')
    modrinth = [ordered]@{
        project = [ordered]@{
            id                    = $modrinthProject.id
            slug                  = $modrinthProject.slug
            title                 = $modrinthProject.title
            description           = $modrinthProject.description
            project_type          = $modrinthProject.project_type
            client_side           = $modrinthProject.client_side
            server_side           = $modrinthProject.server_side
            categories            = @($modrinthProject.categories)
            additional_categories = @($modrinthProject.additional_categories)
            status                = $modrinthProject.status
            license               = $modrinthProject.license
            source_url            = $modrinthProject.source_url
            issues_url            = $modrinthProject.issues_url
        }
        versions = @($modrinthVersions | ForEach-Object { Select-ModrinthVersionMetadata -Version $_ })
    }
    curseforge = [ordered]@{
        project_id                 = $CurseForgeProjectId
        author_api_available       = $curseForgeAuthorApiAvailable
        author_api_error           = $curseForgeAuthorApiError
        public_api_available       = $curseForgePublicApiAvailable
        project                    = $curseForgeProject
        files                      = @($curseForgeFileMetadata)
        relevant_game_versions     = @($curseForgeGameVersions | Where-Object {
            $_.name -in @('Client', 'Server', '1.20.1', '1.21.1', 'Forge', 'NeoForge')
        })
        available_dependency_types = @($curseForgeDependencyTypes)
    }
}

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
$result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $OutputPath -Encoding utf8

$curseForgeFileCount = @($curseForgeFileMetadata).Count
Write-Host "[BRNTalk Release] Audited $($modrinthVersions.Count) Modrinth versions and $curseForgeFileCount CurseForge files."
if (-not $curseForgePublicApiAvailable) {
    Write-Host '[BRNTalk Release] CURSEFORGE_API_KEY is not configured; historical CurseForge files were skipped.' -ForegroundColor Yellow
}
if (-not $curseForgeAuthorApiAvailable) {
    Write-Host '[BRNTalk Release] CurseForge author read endpoints are unavailable; upload access is unaffected.' -ForegroundColor Yellow
}
