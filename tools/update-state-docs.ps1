# Regenerates docs/state-machines.artifact.html from the state machines declared in the code.
# The state_graphs game test fills docs/state-machines.template.html with a diagram and tables per machine.
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try { ./gradlew runGameTestServer --console=plain; Write-Host "Updated docs/state-machines.artifact.html" } finally { Pop-Location }
