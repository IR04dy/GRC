@echo off
setlocal
title TSS GRC AI Gateway
echo ==========================================================
echo    Starting TSS GRC Platform AI Gateway
echo ==========================================================

if "%GATEWAY_PORT%"=="" set GATEWAY_PORT=4000
if "%GATEWAY_MASTER_KEY%"=="" (
    echo [!] GATEWAY_MASTER_KEY is not set. Set it to the backend's AI_GATEWAY_API_KEY value.
    pause
    exit /b 1
)

echo [+] Running Gateway on port %GATEWAY_PORT%...
py -3.10 "%~dp0gateway_server.py"
pause
