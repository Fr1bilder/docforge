@echo off
setlocal
cd /d "%~dp0"
if not exist "target\docforge-1.0.0.jar" (
  echo Сначала выполните build.cmd. Нужны JDK 21 и Maven 3.9+.
  exit /b 1
)
java -jar "target\docforge-1.0.0.jar" %*
exit /b %errorlevel%
