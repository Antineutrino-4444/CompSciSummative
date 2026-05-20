@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package_jar_external.ps1" %*
exit /b %errorlevel%
