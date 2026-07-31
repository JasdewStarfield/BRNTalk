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
Add-Type -AssemblyName System.Net.Http
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

$client = [System.Net.Http.HttpClient]::new()
$form = [System.Net.Http.MultipartFormDataContent]::new()
try {
    $null = $client.DefaultRequestHeaders.TryAddWithoutValidation('Authorization', $Token)
    $null = $client.DefaultRequestHeaders.TryAddWithoutValidation('User-Agent', $headers['User-Agent'])

    # Modrinth parses the data part as JSON; an untyped form string can be rejected before JSON parsing begins.
    $dataContent = [System.Net.Http.StringContent]::new($data, [System.Text.Encoding]::UTF8, 'application/json')
    $fileStream = [System.IO.File]::OpenRead($jar.FullName)
    $fileContent = [System.Net.Http.StreamContent]::new($fileStream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('application/java-archive')
    $form.Add($dataContent, 'data')
    $form.Add($fileContent, 'file', $jar.Name)

    $httpResponse = $client.PostAsync("$ApiBase/version", $form).GetAwaiter().GetResult()
    $responseBody = $httpResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $httpResponse.IsSuccessStatusCode) {
        throw "Modrinth upload failed with HTTP $([int]$httpResponse.StatusCode): $responseBody"
    }
    $response = $responseBody | ConvertFrom-Json
} finally {
    $form.Dispose()
    $client.Dispose()
}
Write-Host "[BRNTalk Release] Modrinth published version id $($response.id)." -ForegroundColor Green
