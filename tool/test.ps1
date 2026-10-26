param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $projectRoot '.test-build/java'
$jdkBin = 'C:\Program Files\Android\Android Studio\jbr\bin'

function Find-Tool([string] $name) {
    $bundled = Join-Path $jdkBin "$name.exe"
    if (Test-Path -LiteralPath $bundled) { return $bundled }
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    throw "Missing required tool: $name"
}

$javac = Find-Tool 'javac'
$java = Find-Tool 'java'
$node = (Get-Command node -ErrorAction Stop).Source
$production = @(
    (Join-Path $projectRoot 'app/src/main/java/com/clarotube/app/AdBlockRules.java'),
    (Join-Path $projectRoot 'app/src/main/java/com/clarotube/app/UrlPolicy.java')
)
$javaTests = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests/java') -Filter '*.java' -Recurse -File | ForEach-Object { $_.FullName })
$scriptTest = Join-Path $projectRoot 'tests/protection.test.cjs'
$failed = $false

$missing = @($production | Where-Object { -not (Test-Path -LiteralPath $_) })
if ($missing.Count -gt 0) {
    Write-Host 'Java tests cannot run: implementation files are missing:' -ForegroundColor Yellow
    $missing | ForEach-Object { Write-Host "  $_" }
    $failed = $true
} elseif ($javaTests.Count -eq 0) {
    Write-Host 'Java tests cannot run: no Java tests were found.' -ForegroundColor Yellow
    $failed = $true
} else {
    New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
    Write-Host 'Compiling standalone Java tests...'
    & $javac --release 17 -encoding UTF-8 -d $buildDir @production @javaTests
    if ($LASTEXITCODE -ne 0) {
        $failed = $true
    } else {
        foreach ($testClass in @('AdBlockRulesTest', 'UrlPolicyTest')) {
            Write-Host "Running $testClass..."
            & $java -cp $buildDir "com.clarotube.app.$testClass"
            if ($LASTEXITCODE -ne 0) { $failed = $true }
        }
    }
}

if (Test-Path -LiteralPath $scriptTest) {
    Write-Host 'Running protection.js tests...'
    & $node --test $scriptTest
    if ($LASTEXITCODE -ne 0) { $failed = $true }
} else {
    Write-Host "JavaScript tests cannot run: missing $scriptTest" -ForegroundColor Yellow
    $failed = $true
}

if ($failed) { exit 1 }
Write-Host 'All standalone tests passed.'
