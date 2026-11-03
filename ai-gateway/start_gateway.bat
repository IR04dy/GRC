@echo off
setlocal
title TSS GRC AI Gateway
echo ==========================================================
echo    Starting TSS GRC Platform AI Gateway
echo ==========================================================

if "%GATEWAY_PORT%"=="" set GATEWAY_PORT=4000
if "%GATEWAY_MASTER_KEY%"=="" set GATEWAY_MASTER_KEY=sk-tss-grc-gateway-master-key

echo [+] Running Gateway on port %GATEWAY_PORT%...
py -3.10 "%~dp0gateway_server.py"
pause
