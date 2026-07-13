[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Command,

    [string]$HostName = '127.0.0.1',
    [int]$Port = 25575,
    [string]$Password = 'brntalk-debug'
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'DebugTools.Common.ps1')

$response = Invoke-BrntalkRconCommand -HostName $HostName -Port $Port -Password $Password -Command $Command
if (-not [string]::IsNullOrWhiteSpace($response)) {
    Write-Output $response
}
