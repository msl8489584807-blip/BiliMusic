param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Tasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug'))
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
if (Test-Path -LiteralPath (Join-Path $projectDir '.tooling\gradle-home')) { $env:GRADLE_USER_HOME = Join-Path $projectDir '.tooling\gradle-home' }
$jdkCandidates = @($env:JAVA_HOME, (Join-Path $projectDir '.tooling\jdk-17'))
foreach ($jdkPath in $jdkCandidates) {
    if ($jdkPath -and (Test-Path -LiteralPath (Join-Path $jdkPath 'bin\java.exe'))) {
        $env:JAVA_HOME = $jdkPath
        break
    }
}
Push-Location $projectDir
try { & .\gradlew.bat @Tasks; if ($LASTEXITCODE -ne 0) { throw "Gradle exited $LASTEXITCODE" } } finally { Pop-Location }
