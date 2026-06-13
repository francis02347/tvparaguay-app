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

if ($content -match 'versionCode\s*=?\s*(\d+)') {
    $currentVersionCode = [int]$Matches[1]
} else {
    Write-Host "[ERROR] No se pudo extraer el 'versionCode' de build.gradle" -ForegroundColor Red
    exit
}

if ($content -match 'versionName\s*=?\s*["'']([^"'']+)["'']') {
    $currentVersionName = $Matches[1]
} else {
    Write-Host "[ERROR] No se pudo extraer el 'versionName' de build.gradle" -ForegroundColor Red
    exit
}

# 4. Calcular versiones sugeridas automaticamente
$newVersionCode = $currentVersionCode + 1

# Dividir versionName por puntos (ej: "1.37" -> 1 y 37)
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
$newContent = $content -replace "versionCode\s*=\s*$currentVersionCode", "versionCode = $newVersionCode"
$newContent = $newContent -replace "versionCode\s+$currentVersionCode", "versionCode $newVersionCode"
$newContent = $newContent -replace "versionName\s*=\s*['\`"]$currentVersionName['\`"]", "versionName = `"$newVersionName`""
$newContent = $newContent -replace "versionName\s+['\`"]$currentVersionName['\`"]", "versionName `"$newVersionName`""
Set-Content -Path $gradlePath -Value $newContent -NoNewLine

Write-Host "[OK] Archivo build.gradle modificado con exito." -ForegroundColor Green
Write-Host ""

# 7. Buscar JDK compatible
if ([string]::IsNullOrEmpty($env:JAVA_HOME) -or -not (Test-Path $env:JAVA_HOME)) {
    $commonJdks = @(
        "D:\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jbr",
        "D:\Android Studio\jre",
        "C:\Program Files\Android\Android Studio\jre"
    )
    foreach ($jdk in $commonJdks) {
        if (Test-Path $jdk) {
            $env:JAVA_HOME = $jdk
            $env:PATH = "$(Join-Path $jdk 'bin');$env:PATH"
            Write-Host "[INFO] Usando JDK compatible en: $jdk" -ForegroundColor Gray
            break
        }
    }
}

# 8. Compilar APK localmente
Write-Host "[INFO] Iniciando compilacion local de la version Website..." -ForegroundColor Cyan
$gradleExe = "./gradlew.bat"
if (-not (Test-Path $gradleExe)) {
    $gradleExe = "./gradlew"
}
if (-not (Test-Path $gradleExe)) {
    $gradleExe = "gradle"
}

& $gradleExe assembleWebsiteRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] La compilacion local ha fallado. Revisa los errores." -ForegroundColor Red
    exit
}
Write-Host "[OK] Compilacion completada con exito." -ForegroundColor Green
Write-Host ""

# 9. Encontrar el archivo APK compilado
$apkPath = "app/build/outputs/apk/website/release/app-website-release.apk"
if (-not (Test-Path $apkPath)) {
    $apkPath = Get-ChildItem -Path app/build -Filter *website-release*.apk -Recurse | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $apkPath -or -not (Test-Path $apkPath)) {
    $apkPath = Get-ChildItem -Path app/build -Filter *.apk -Recurse | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $apkPath -or -not (Test-Path $apkPath)) {
    Write-Host "[ERROR] No se pudo encontrar el archivo APK compilado." -ForegroundColor Red
    exit
}
Write-Host "[INFO] APK encontrado en: $apkPath" -ForegroundColor Gray
Write-Host ""

# 10. Buscar token de GitHub
$githubToken = $env:GITHUB_TOKEN
if ([string]::IsNullOrEmpty($githubToken)) {
    # Intentar buscar en bot_config.json en directorios conocidos
    $pathsToTry = @(
        "bot_config.json",
        "../bot_config.json",
        "../../bot_config.json",
        "../Antigravity_Proyectos/TV Paraguay/bot_config.json",
        "D:/Antigravity_Proyectos/TV Paraguay/bot_config.json",
        "C:/Users/Francisco/OneDrive/Reloj/bot_config.json"
    )
    foreach ($p in $pathsToTry) {
        if (Test-Path $p) {
            try {
                $json = Get-Content $p -Raw | ConvertFrom-Json
                if ($json.GITHUB_TOKEN) {
                    $githubToken = $json.GITHUB_TOKEN
                    break
                }
            } catch {}
        }
    }
}

if ([string]::IsNullOrEmpty($githubToken) -or $githubToken -eq "TU_GITHUB_TOKEN_AQUI") {
    Write-Host "[WARNING] No se encontro GITHUB_TOKEN. Solo se guardaran los cambios locales." -ForegroundColor Yellow
} else {
    Write-Host "[INFO] Creando Release en GitHub y subiendo APK..." -ForegroundColor Cyan
    
    $owner = "francis02347"
    $repo = "tvparaguay-app"
    $tagName = "v$newVersionName"
    
    # Git commit de los cambios de version en build.gradle
    Write-Host "[INFO] Registrando cambios de version en Git local..." -ForegroundColor Gray
    git add app/build.gradle
    git commit -m "Version bump to $newVersionName [skip ci]"
    git push origin main
    
    # Crear etiqueta y subirla
    Write-Host "[INFO] Etiquetando como $tagName..." -ForegroundColor Gray
    git tag -d $tagName 2>$null
    git tag $tagName
    git push origin :refs/tags/$tagName 2>$null
    git push origin $tagName
    
    # 1. Crear el Release en GitHub
    $headers = @{
        "Authorization" = "Bearer $githubToken"
        "Accept"        = "application/vnd.github.v3+json"
        "User-Agent"    = "PowerShell-Release-Script"
    }
    
    $releaseBody = @{
        "tag_name"         = $tagName
        "target_commitish" = "main"
        "name"             = "Release $tagName"
        "body"             = "Release automatizada de la version $newVersionName"
        "draft"            = $false
        "prerelease"       = $false
    } | ConvertTo-Json
    
    $releaseUrl = "https://api.github.com/repos/$owner/$repo/releases"
    try {
        $releaseResponse = Invoke-RestMethod -Uri $releaseUrl -Headers $headers -Method Post -Body $releaseBody -ContentType "application/json"
        $releaseId = $releaseResponse.id
        Write-Host "[OK] Release creada en GitHub (ID: $releaseId)." -ForegroundColor Green
        
        # 2. Subir el APK como asset a la release
        Write-Host "[INFO] Subiendo APK a la Release..." -ForegroundColor Cyan
        $uploadUrl = "https://uploads.github.com/repos/$owner/$repo/releases/$releaseId/assets?name=app-website-release.apk"
        
        # Leer los bytes del APK
        $fileBytes = [System.IO.File]::ReadAllBytes($apkPath)
        
        $uploadHeaders = @{
            "Authorization" = "Bearer $githubToken"
            "Accept"        = "application/vnd.github.v3+json"
            "User-Agent"    = "PowerShell-Release-Script"
        }
        
        $uploadResponse = Invoke-RestMethod -Uri $uploadUrl -Headers $uploadHeaders -Method Post -Body $fileBytes -ContentType "application/vnd.android.package-archive"
        $downloadUrl = $uploadResponse.browser_download_url
        Write-Host "[OK] APK subida correctamente: $downloadUrl" -ForegroundColor Green
        
        # 3. Actualizar update.json
        Write-Host "[INFO] Actualizando update.json..." -ForegroundColor Cyan
        $updateJsonPath = "update.json"
        
        $updateInfo = [PSCustomObject]@{
            versionCode = [int]$newVersionCode
            versionName = $newVersionName
            apkUrl = $downloadUrl
            releaseNotes = "Actualización automática de la versión completa (sabor web) construida para la versión $tagName."
        }
        
        $updateInfo | ConvertTo-Json -Depth 5 | Set-Content -Path $updateJsonPath
        
        # Subir update.json a GitHub
        Write-Host "[INFO] Subiendo update.json a GitHub..." -ForegroundColor Gray
        git add update.json
        git commit -m "chore: auto-update update.json to $newVersionName [skip ci]"
        git push origin main
        Write-Host "[OK] Proceso finalizado. update.json actualizado en GitHub con exito." -ForegroundColor Green
        
        # Lanzar Notificacion de Windows Nativa (Balloon Tip)
        try {
            Add-Type -AssemblyName System.Windows.Forms
            Add-Type -AssemblyName System.Drawing
            $notify = New-Object System.Windows.Forms.NotifyIcon
            $notify.Icon = [System.Drawing.SystemIcons]::Information
            $notify.BalloonTipTitle = "TVParaguay - Publicada!"
            $notify.BalloonTipText = "La version $newVersionName ya esta publicada en GitHub y lista para descargar."
            $notify.BalloonTipIcon = [System.Windows.Forms.ToolTipIcon]::Info
            $notify.Visible = $true
            $notify.ShowBalloonTip(8000)
            Start-Sleep -Seconds 2
            $notify.Dispose()
        } catch {}
        
    } catch {
        Write-Host "[ERROR] Fallo al crear la Release o subir la APK a GitHub:" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
    }
}
