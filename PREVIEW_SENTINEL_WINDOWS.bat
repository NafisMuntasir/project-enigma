@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat lwjgl3:run --args="--sprite-preview"
if errorlevel 1 pause
