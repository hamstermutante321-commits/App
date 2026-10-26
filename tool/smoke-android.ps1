#requires -Version 7.0
param(
    [string]$Serial = 'emulator-5554',
    [string]$Adb = 'adb'
)

$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Esta prueba modifica solo un emulador, no un teléfono personal.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$artifactDir = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

function Invoke-Adb([string[]]$Arguments) {
    $ErrorActionPreference = 'Continue'
    $output = & $Adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
    return ($output -join "`n")
}

function Read-Screen([string]$Name) {
    Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/clarotube-smoke.xml') | Out-Null
    $path = Join-Path $artifactDir "$Name.xml"
    Invoke-Adb @('pull', '/sdcard/clarotube-smoke.xml', $path) | Out-Null
    return [xml](Get-Content -Raw -LiteralPath $path)
}

function Tap-Node($Node) {
    if ($null -eq $Node) { throw 'No se encontró el control esperado.' }
    $bounds = @([regex]::Matches($Node.bounds, '\d+') | ForEach-Object { [int]$_.Value })
    $x = [int](($bounds[0] + $bounds[2]) / 2)
    $y = [int](($bounds[1] + $bounds[3]) / 2)
    Invoke-Adb @('shell', 'input', 'tap', "$x", "$y") | Out-Null
}

function Find-Description($Screen, [string]$Description) {
    return $Screen.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $Description } | Select-Object -First 1
}

$rotation = (Invoke-Adb @('shell', 'settings', 'get', 'system', 'user_rotation')).Trim()
$autoRotation = (Invoke-Adb @('shell', 'settings', 'get', 'system', 'accelerometer_rotation')).Trim()
try {
    Invoke-Adb @('install', '-r', (Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk')) | Out-Null
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0') | Out-Null
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'user_rotation', '0') | Out-Null
    Invoke-Adb @('shell', 'am', 'force-stop', 'com.clarotube.app') | Out-Null
    Invoke-Adb @('shell', 'am', 'start', '-W', '-n', 'com.clarotube.app/.MainActivity') | Out-Null
    $screen = Read-Screen 'smoke-start'
    $welcome = $screen.SelectNodes('//node') | Where-Object { $_.text -eq 'EMPEZAR' } | Select-Object -First 1
    if ($welcome) { Tap-Node $welcome; $screen = Read-Screen 'smoke-home' }
    if (Find-Description $screen 'Activar protección') {
        Tap-Node (Find-Description $screen 'Activar protección')
        $screen = Read-Screen 'smoke-enabled'
    }
    Tap-Node (Find-Description $screen 'Desactivar protección')
    $screen = Read-Screen 'smoke-disabled'
    if (-not (Find-Description $screen 'Activar protección')) { throw 'El escudo no se desactivó.' }

    Invoke-Adb @('shell', 'am', 'force-stop', 'com.clarotube.app') | Out-Null
    Invoke-Adb @('shell', 'am', 'start', '-W', '-n', 'com.clarotube.app/.MainActivity') | Out-Null
    $screen = Read-Screen 'smoke-persisted'
    if (-not (Find-Description $screen 'Activar protección')) { throw 'La preferencia no persistió.' }
    Tap-Node (Find-Description $screen 'Activar protección')
    $screen = Read-Screen 'smoke-restored'
    if (-not (Find-Description $screen 'Desactivar protección')) { throw 'El escudo no se reactivó.' }

    Tap-Node (Find-Description $screen 'Abrir ajustes y ayuda')
    $screen = Read-Screen 'smoke-settings'
    if (-not ($screen.SelectNodes('//node') | Where-Object { $_.text -eq 'Cómo funciona la protección' })) {
        throw 'No se abrieron las opciones.'
    }
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'user_rotation', '1') | Out-Null
    $screen = Read-Screen 'smoke-landscape'
    if (-not (Find-Description $screen 'Abrir búsqueda o enlace') -or -not (Find-Description $screen 'Abrir ajustes y ayuda')) {
        throw 'Faltan controles en horizontal.'
    }
    Invoke-Adb @('shell', 'screencap', '-p', '/sdcard/clarotube-landscape.png') | Out-Null
    Invoke-Adb @('pull', '/sdcard/clarotube-landscape.png', (Join-Path $artifactDir 'landscape.png')) | Out-Null
    Write-Host 'PASS: inicio, escudo, persistencia, opciones y controles en horizontal.'
} finally {
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'user_rotation', $rotation) | Out-Null
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', $autoRotation) | Out-Null
}
