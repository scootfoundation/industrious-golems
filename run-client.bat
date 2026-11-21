@echo off
rem Boots the dev client (Minecraft 26.3 + this mod; Gradle fetches Java 25 itself). Connect via Multiplayer to localhost:25565
rem if the dev server (run-server.bat) is running. While it runs, tools\hotswap.ps1 pushes recompiled code into it.
cd /d "%~dp0"

rem Your JAVA_HOME was invalid (C:\Progra~1\Java), so fall back to the installed Corretto 21 JDK.
if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME=C:\Program Files\Amazon Corretto\jdk21.0.2_13"

call gradlew.bat runClient %*
pause
