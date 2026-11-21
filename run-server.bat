@echo off
rem Boots the dev server (Minecraft 26.3 + this mod) on localhost:25565 (offline mode, flat world; config in run\server).
rem While it runs, tools\hotswap.ps1 -Server pushes recompiled code into it.
cd /d "%~dp0"

if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME=C:\Program Files\Amazon Corretto\jdk21.0.2_13"

call gradlew.bat runServer %*
pause
