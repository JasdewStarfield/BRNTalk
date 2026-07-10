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

    [string]$ApiBase = 'https://minecraft.curseforge.com/api'
)

$ErrorActionPreference = 'Stop'
$jar = Get-Item -LiteralPath $JarPath
$rendered = Get-Content -LiteralPath $MetadataPath -Raw -Encoding utf8 | ConvertFrom-Json
if ($jar.Name -ne $rendered.jar_name) {
    throw "Jar '$($jar.Name)' does not match rendered metadata jar '$($rendered.jar_name)'."
}
$metadata = $rendered.curseforge | ConvertTo-Json -Depth 10 -Compress

if (-not $PSCmdlet.ShouldProcess("CurseForge project $ProjectId", "Upload $($jar.Name) as $($rendered.curseforge.displayName)")) {
    return
}

$request = @{
    Method  = 'Post'
    Uri     = "$ApiBase/projects/$ProjectId/upload-file"
    Headers = @{ 'X-Api-Token' = $Token }
    Form    = @{ metadata = $metadata; file = $jar }
}
$response = Invoke-RestMethod @request
Write-Host "[BRNTalk Release] CurseForge accepted file id $($response.id)." -ForegroundColor Green
