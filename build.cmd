@echo off
setlocal
cd /d "%~dp0"
call mvn -B clean verify
exit /b %errorlevel%
