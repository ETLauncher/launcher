param(
    [string]$IsccPath = $env:INNO_SETUP_ISCC
)

$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path -LiteralPath (Join-Path $jdk 'bin\jlink.exe'))) {
    throw 'Set JAVA_HOME to a JDK 17 installation before building the installer.'
}
if (-not $IsccPath) {
    $compiler = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($compiler) { $IsccPath = $compiler.Source }
}
if (-not $IsccPath -or -not (Test-Path -LiteralPath $IsccPath)) {
    throw 'Pass -IsccPath with the location of Inno Setup 7 ISCC.exe.'
}

Push-Location $project
try {
    & .\gradlew.bat test shadowJar createExe --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }

    $dist = Join-Path $project 'dist'
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $project 'build\launch4j\ETLauncher-3.4.41.4.exe') `
        -Destination (Join-Path $dist 'ETLauncher.exe') -Force

    $runtime = Join-Path $dist 'jre'
    if (-not (Test-Path -LiteralPath (Join-Path $runtime 'bin\java.exe'))) {
        if (Test-Path -LiteralPath $runtime) {
            throw 'The dist\jre directory is incomplete. Move it aside before rebuilding.'
        }
        & (Join-Path $jdk 'bin\jlink.exe') --module-path (Join-Path $jdk 'jmods') `
            --add-modules ALL-MODULE-PATH --strip-debug --no-man-pages --no-header-files `
            --compress=2 --output $runtime
        if ($LASTEXITCODE -ne 0) { throw 'Java runtime creation failed.' }
    }

    & $IsccPath '/Q' (Join-Path $PSScriptRoot 'installer.iss')
    if ($LASTEXITCODE -ne 0) { throw 'Inno Setup compilation failed.' }
    Write-Output (Join-Path $dist 'ETLauncher-setup-3.4.41.4.exe')
} finally {
    Pop-Location
}
