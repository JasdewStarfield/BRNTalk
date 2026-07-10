[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory)]
    [string]$ProjectId,

    [Parameter(Mandatory)]
    [string]$Token,

    [Parameter(Mandatory)]
    [string]$JarPath,

    [Parameter(Mandatory)]
    [string]$MetadataPath,

    [string]$ApiBase = 'https://api.modrinth.com/v2'
)

$ErrorActionPreference = 'Stop'
$jar = Get-Item -LiteralPath $JarPath
$metadata = Get-Content -LiteralPath $MetadataPath -Raw -Encoding utf8 | ConvertFrom-Json
if ($jar.Name -ne $metadata.jar_name) {
    throw "Jar '$($jar.Name)' does not match rendered metadata jar '$($metadata.jar_name)'."
}

$payload = [ordered]@{}
foreach ($property in $metadata.modrinth.PSObject.Properties) {
    $payload[$property.Name] = $property.Value
}
$payload.project_id = $ProjectId
$platformVersion = $payload.version_number
$data = $payload | ConvertTo-Json -Depth 10 -Compress

if (-not $PSCmdlet.ShouldProcess("Modrinth project $ProjectId", "Publish $($jar.Name) as $platformVersion")) {
    return
}

$headers = @{
    Authorization = $Token
    'User-Agent'  = 'JasdewStarfield/BRNTalk-release-automation (github.com/JasdewStarfield/BRNTalk)'
}

# Modrinth version numbers are unique per project, so a rerun safely skips an existing platform build.
$existingVersions = Invoke-RestMethod -Method Get -Uri "$ApiBase/project/$ProjectId/version" -Headers $headers
if ($existingVersions.version_number -contains $platformVersion) {
    Write-Host "[BRNTalk Release] Modrinth version $platformVersion already exists; skipping." -ForegroundColor Yellow
    return
}

$response = Invoke-RestMethod -Method Post -Uri "$ApiBase/version" -Headers $headers -Form @{
    data = $data
    file = $jar
}
Write-Host "[BRNTalk Release] Modrinth published version id $($response.id)." -ForegroundColor Green
