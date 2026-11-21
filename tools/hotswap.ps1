# Compiles, then pushes the changed classes into the running dev client (or the dev server with -Server).
# Resources (textures, animation and timing JSON) are copied too: press F3+T in the client for assets, or run /reload for data.
#   powershell -ExecutionPolicy Bypass -File tools/hotswap.ps1            # client, debug port 5005
#   powershell -ExecutionPolicy Bypass -File tools/hotswap.ps1 -Server    # dev server, debug port 5006
param([switch]$Server)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk21.0.2_13' }
$port = if ($Server) { 5006 } else { 5005 }
& .\gradlew.bat classes --console=plain -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$env:JAVA_HOME\bin\java.exe" tools/Hotswap.java $port
