param(
  [string]$OutputDir = "static/img/screenshots",
  [string]$AdminUrl = "http://localhost:5173",
  [string]$ModelerUrl = "http://localhost:3000",
  [string]$PortalUrl = "http://localhost:5174"
)

$ErrorActionPreference = "Stop"

function Ensure-Dir([string]$Path) {
  if (-not (Test-Path $Path)) {
    New-Item -ItemType Directory -Path $Path | Out-Null
  }
}

Ensure-Dir $OutputDir

Write-Host "Capturing Easy BPM Admin screenshot..."
npx -y playwright screenshot --device="Desktop Chrome" "$AdminUrl" "$OutputDir/admin-home.png"

Write-Host "Capturing Easy BPMN Modeler screenshot..."
npx -y playwright screenshot --device="Desktop Chrome" "$ModelerUrl" "$OutputDir/modeler-home.png"

Write-Host "Capturing Easy BPM Task Portal screenshot..."
npx -y playwright screenshot --device="Desktop Chrome" "$PortalUrl" "$OutputDir/task-portal-home.png"

Write-Host "All screenshots captured into $OutputDir"
