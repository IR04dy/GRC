@echo off
echo Starting TSS GRC UI on http://localhost:8086/login.html
echo.
cd /d "%~dp0"

:: Try java in PATH first, then fallback to common install location
where java >nul 2>&1
if %errorlevel% == 0 (
    java StaticFileServer.java
) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
    "C:\Program Files\Java\jdk-17\bin\java.exe" StaticFileServer.java
) else (
    echo ERROR: Java not found. Please add Java to your PATH.
    pause
)
