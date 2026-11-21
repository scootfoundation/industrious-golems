# Compiles, then pushes the changed classes into the running dev client (or the dev server with -Server) without a restart.
# Method bodies only: a new field or method is reported as needing a restart. Resources (textures, animation and timing JSON)
# are copied by the build too: press F3+T in the client for assets, or run /reload for data.
#   powershell -ExecutionPolicy Bypass -File tools/hotswap.ps1            # client
#   powershell -ExecutionPolicy Bypass -File tools/hotswap.ps1 -Server    # dev server
param([switch]$Server)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk21.0.2_13' }
$jdk = $env:JAVA_HOME
$which = if ($Server) { 'server' } else { 'client' }
& .\gradlew.bat classes --console=plain -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$agent = 'build\hotswap\agent.jar'
if (-not (Test-Path $agent) -or (Get-Item tools\agent\HotswapAgent.java).LastWriteTime -gt (Get-Item $agent).LastWriteTime) {
    New-Item -ItemType Directory -Force build\hotswap\out | Out-Null
    & "$jdk\bin\javac.exe" -d build\hotswap\out tools\agent\HotswapAgent.java
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Set-Content build\hotswap\manifest.txt "Agent-Class: HotswapAgent`nCan-Redefine-Classes: true`n"
    & "$jdk\bin\jar.exe" cfm $agent build\hotswap\manifest.txt -C build\hotswap\out .
}
& "$jdk\bin\java.exe" tools/Hotswap.java $which $agent build/classes/java/main
