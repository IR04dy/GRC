<#
.SYNOPSIS
    Launches the TSS GRC AI Gateway on port 4000.
.DESCRIPTION
    Starts the AI Gateway with Cache and Open-Source Guardrails
    (PII Masking, Prompt Injection Defense, Hallucination Detection).
#>

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   Starting TSS GRC Platform AI Gateway                  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$Port = if ($env:GATEWAY_PORT) { $env:GATEWAY_PORT } else { "4000" }
$env:GATEWAY_PORT = $Port
$env:GATEWAY_MASTER_KEY = if ($env:GATEWAY_MASTER_KEY) { $env:GATEWAY_MASTER_KEY } else { "sk-tss-grc-gateway-master-key" }

# Check Python 3.10 or py launcher
if (Get-Command py -ErrorAction SilentlyContinue) {
    $PythonCmd = "py -3.10"
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    $PythonCmd = "python"
} else {
    Write-Error "Python is not found in PATH or via 'py' launcher. Please install Python 3.9+."
    exit 1
}

Write-Host "[+] Using Python runtime: $PythonCmd" -ForegroundColor Green
Write-Host "[+] AI Gateway Port: $Port" -ForegroundColor Green
Write-Host "[+] Master Virtual Key: $env:GATEWAY_MASTER_KEY" -ForegroundColor Green
Write-Host "[+] Guardrails: PII Masking (PDPL), Prompt Injection, Hallucination Grounding" -ForegroundColor Green
Write-Host "[+] Endpoints:" -ForegroundColor Yellow
Write-Host "    - OpenAI API : http://localhost:$Port/v1/chat/completions" -ForegroundColor Yellow
Write-Host "    - Health     : http://localhost:$Port/health" -ForegroundColor Yellow
Write-Host "    - Metrics    : http://localhost:$Port/metrics" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# Launch server
& py -3.10 "$PSScriptRoot\gateway_server.py"
