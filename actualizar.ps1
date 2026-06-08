# Script para automatizar la actualización local y el lanzamiento a GitHub de TVParaguay

[CmdletBinding()]
param (
    [switch]$Auto
)

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "   ACTUALIZADOR AUTOMATICO DE TVPARAGUAY" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# 1. Verificar la ruta del archivo app/build.gradle
$gradlePath = "app/build.gradle"
if (-not (Test-Path $gradlePath)) {
    Write-Host "[ERROR] No se pudo encontrar el archivo $gradlePath." -ForegroundColor Red
    Write-Host "Asegurate de estar ejecutando este script desde la carpeta raiz del proyecto." -ForegroundColor Yellow
    exit
}

# 2. Leer el contenido de build.gradle
$content = Get-Content $gradlePath -Raw

# 3. Extraer versionCode y versionName actuales
$currentVersionCode = $null
$currentVersionName = $null

if ($content -match 'versionCode\s+(\d+)') {
    $currentVersionCode = [int]$Matches[1]
} else {
    Write-Host "[ERROR] No se pudo extraer el 'versionCode' de build.gradle" -ForegroundColor Red
    exit
}

if ($content -match 'versionName\s+["'']([^"'']+)["'']') {
    $currentVersionName = $Matches[1]
} else {
    Write-Host "[ERROR] No se pudo extraer el 'versionName' de build.gradle" -ForegroundColor Red
    exit
}

# 4. Calcular versiones sugeridas automaticamente
$newVersionCode = $currentVersionCode + 1

# Dividir versionName por puntos (ej: "1.1" -> 1 y 1)
$versionParts = $currentVersionName -split '\.'
if ($versionParts.Count -ge 2) {
    $major = [int]$versionParts[0]
    $minor = [int]$versionParts[1]
    $newMinor = $minor + 1
    $suggestedVersionName = "$major.$newMinor"
} else {
    $suggestedVersionName = "$currentVersionName.1"
}

Write-Host "Version actual detectada:" -ForegroundColor White
Write-Host "  [i] Nombre de version: $currentVersionName" -ForegroundColor Yellow
Write-Host "  [i] Codigo de version: $currentVersionCode" -ForegroundColor Yellow
Write-Host ""
Write-Host "Version sugerida para la actualizacion:" -ForegroundColor White
Write-Host "  [i] Nuevo Nombre de version: $suggestedVersionName" -ForegroundColor Green
Write-Host "  [i] Nuevo Codigo de version: $newVersionCode" -ForegroundColor Green
Write-Host ""

# 5. Preguntar al usuario confirmacion
$newVersionName = ""
if ($Auto) {
    Write-Host "[AUTO] Bumping version code to $newVersionCode and name to $suggestedVersionName automatically." -ForegroundColor Green
    $newVersionName = $suggestedVersionName
} else {
    Write-Host "Escribi 'S' para usar la sugerida, ingresa un nombre personalizado (ej: 1.3), o presiona 'N' para cancelar:" -ForegroundColor Gray
    $userInput = Read-Host "Opcion"
    if ($userInput -eq "N" -or $userInput -eq "n") {
        Write-Host "[INFO] Proceso cancelado por el usuario." -ForegroundColor Red
        exit
    } elseif ([string]::IsNullOrEmpty($userInput) -or $userInput -eq "S" -or $userInput -eq "s") {
        $newVersionName = $suggestedVersionName
    } else {
        $newVersionName = $userInput.Trim()
    }
}

Write-Host ""
Write-Host "[INFO] Aplicando cambios en build.gradle..." -ForegroundColor Cyan

# 6. Reemplazar versionCode y versionName en el archivo build.gradle
$newContent = $content -replace "versionCode\s+$currentVersionCode", "versionCode $newVersionCode"
$newContent = $newContent -replace "versionName\s+['\`"]$currentVersionName['\`"]", "versionName `"$newVersionName`""
Set-Content -Path $gradlePath -Value $newContent -NoNewLine

Write-Host "[OK] Archivo build.gradle modificado con exito." -ForegroundColor Green
Write-Host ""
Write-Host "[INFO] Iniciando comandos de Git..." -ForegroundColor Cyan
Write-Host ""

# 7. Ejecutar comandos de Git
Write-Host "[INFO] 1. Agregando archivos al commit (git add .)..." -ForegroundColor Gray
git add .
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] Error en git add" -ForegroundColor Red; exit }

$commitMessage = "Release version $newVersionName"
Write-Host "[INFO] 2. Creando commit: `"$commitMessage`"..." -ForegroundColor Gray
git commit -m $commitMessage
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] Error en git commit" -ForegroundColor Red; exit }

Write-Host "[INFO] 3. Sincronizando cambios desde GitHub (git pull --rebase origin main)..." -ForegroundColor Gray
git pull --rebase origin main
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] Error al sincronizar cambios desde GitHub." -ForegroundColor Red; exit }

Write-Host "[INFO] 4. Subiendo cambios a GitHub en rama 'main' (git push origin main)..." -ForegroundColor Gray
git push origin main
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] Error al subir a rama main. Asegurate de tener configurado tu repositorio remoto." -ForegroundColor Red; exit }

$tagName = "v$newVersionName"
Write-Host "[INFO] 4. Creando etiqueta de version: $tagName..." -ForegroundColor Gray
git tag $tagName
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] Error en git tag" -ForegroundColor Red; exit }

Write-Host "[INFO] 5. Subiendo etiqueta a GitHub (git push origin $tagName)..." -ForegroundColor Gray
git push origin $tagName
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] Error al subir etiqueta" -ForegroundColor Red; exit }

Write-Host ""
Write-Host "[OK] El proceso de Git y el envio de cambios han finalizado!" -ForegroundColor Green
Write-Host ""

# 8. Monitoreo opcional de compilacion en GitHub
$shouldWait = $false
if ($Auto) {
    Write-Host "[AUTO] Esperando compilacion en la nube automaticamente..." -ForegroundColor Green
    $shouldWait = $true
} else {
    $waitConfirm = Read-Host "[?] Deseas esperar a que la compilacion en la nube finalice para recibir una notificacion? (S/N)"
    if ($waitConfirm -eq "S" -or $waitConfirm -eq "s" -or $waitConfirm -eq "") {
        $shouldWait = $true
    }
}

if ($shouldWait) {
    Write-Host ""
    Write-Host "[WAIT] Conectando con GitHub Actions para monitorear la compilacion..." -ForegroundColor Cyan
    Write-Host "Este proceso suele tardar entre 2 y 3 minutos mientras GitHub compila el APK." -ForegroundColor Gray
    Write-Host "Podes minimizar esta ventana; te avisaremos con una notificacion de Windows al finalizar." -ForegroundColor Gray
    Write-Host ""
    Write-Host "Monitoreando" -NoNewline -ForegroundColor Cyan

    $released = $false
    $attempts = 0
    $maxAttempts = 24
    $apiUrl = "https://api.github.com/repos/francis02347/tvparaguay-app/releases/tags/$tagName"

    while (-not $released -and $attempts -lt $maxAttempts) {
        Start-Sleep -Seconds 10
        $attempts++
        Write-Host "." -NoNewline -ForegroundColor Cyan
        try {
            $headers = @{ "User-Agent" = "Mozilla/5.0" }
            $res = Invoke-RestMethod -Uri $apiUrl -Headers $headers -Method Get -ErrorAction Stop
            if ($res -and $res.assets -and $res.assets.Count -gt 0) {
                $released = $true
            }
        } catch {
            # 404 es normal mientras compila
        }
    }

    Write-Host ""
    if ($released) {
        Write-Host ""
        Write-Host "==========================================================" -ForegroundColor Green
        Write-Host " [SUCCESS] ACTUALIZACION PUBLICADA EXITOSAMENTE EN LA NUBE " -ForegroundColor Green
        Write-Host " La version v$newVersionName ya esta disponible para tu TV." -ForegroundColor Green
        Write-Host "==========================================================" -ForegroundColor Green
        Write-Host ""

        # Reproducir sonido de notificacion
        try {
            [System.Media.SystemSounds]::Asterisk.Play()
        } catch {}

        # Lanzar Notificacion de Windows Nativa (Balloon Tip)
        try {
            Add-Type -AssemblyName System.Windows.Forms
            Add-Type -AssemblyName System.Drawing
            $notify = New-Object System.Windows.Forms.NotifyIcon
            $notify.Icon = [System.Drawing.SystemIcons]::Information
            $notify.BalloonTipTitle = "TVParaguay - Version publicada!"
            $notify.BalloonTipText = "La version $newVersionName ya esta publicada en GitHub y lista para descargar en tu TV."
            $notify.BalloonTipIcon = [System.Windows.Forms.ToolTipIcon]::Info
            $notify.Visible = $true
            $notify.ShowBalloonTip(8000)
            
            Start-Sleep -Seconds 2
            $notify.Dispose()
        } catch {}

        # El APK no se descarga localmente para ahorrar espacio de disco.
        Write-Host "[INFO] El APK se encuentra disponible directamente en el repositorio de GitHub." -ForegroundColor Gray

    } else {
        Write-Host ""
        Write-Host "[WARN] La compilacion esta tardando mas de lo habitual en la nube." -ForegroundColor Yellow
        Write-Host "Se agoto el tiempo de espera del script, pero podes seguir el estado en la web:" -ForegroundColor Gray
        Write-Host "https://github.com/francis02347/tvparaguay-app/actions" -ForegroundColor Cyan
        Write-Host ""
    }
} else {
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host " [OK] Los cambios locales y etiquetas han sido subidos!    " -ForegroundColor Green
    Write-Host " GitHub continuara compilando tu APK en segundo plano.    " -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host ""
}
