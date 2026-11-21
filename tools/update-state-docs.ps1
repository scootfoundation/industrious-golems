# Regenerates docs/state-machines.html (and the artifact fragment) from the state machines declared in the code.
# The state_graphs game test writes docs/state-machines.json; this inlines it into the template.
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    ./gradlew runGameTestServer --console=plain
    $json = (Get-Content "$root/docs/state-machines.json" -Raw).Trim()
    $page = (Get-Content "$root/docs/state-machines.template.html" -Raw).Replace('/*DATA*/[]', $json)
    Set-Content "$root/docs/state-machines.html" $page -Encoding utf8
    # the artifact form: title, meta, style and body content only (the publisher adds the document skeleton)
    $style = [regex]::Match($page, '(?s)<style>.*?</style>').Value
    $body = [regex]::Match($page, '(?s)<body>(.*)</body>').Groups[1].Value.Trim()
    $head = "<title>Golem State Machines</title>`n<meta name=`"claude:viewport-fit-sides`" content=`"cover`">`n"
    Set-Content "$root/docs/state-machines.artifact.html" ($head + $style + "`n" + $body + "`n") -Encoding utf8
    Write-Host "Wrote docs/state-machines.html and docs/state-machines.artifact.html"
} finally { Pop-Location }
