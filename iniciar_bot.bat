@echo off
title Iniciar Bot de Desarrollo
set PYTHONUTF8=1
set PYTHONIOENCODING=utf-8
echo ==================================================
echo       INICIANDO BOT DE DESARROLLO REMOTO         
echo ==================================================
powershell -Command "Get-CimInstance Win32_Process -Filter \"CommandLine like '%%telegram_developer_bot.py%%'\" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
powershell -Command "Get-CimInstance Win32_Process -Filter \"CommandLine like '%%actualizar.ps1%%'\" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
timeout /t 1 >nul

echo.
echo Iniciando el bot en segundo plano. Por favor, espera...

:: Borrar log anterior si existe
if exist bot_log.txt del bot_log.txt

:: Iniciar el bot de forma desacoplada
wscript.exe start_detached.vbs

:: Bucle de espera y verificación (máximo 15 segundos)
set "success=0"
for /l %%i in (1,1,30) do (
    timeout /t 1 >nul
    if exist bot_log.txt (
        findstr /c:"Listo para nuevos mensajes" bot_log.txt >nul
        if not errorlevel 1 (
            set "success=1"
            goto :checked
        )
        findstr /c:"ERROR" bot_log.txt >nul
        if not errorlevel 1 goto :checked
        findstr /c:"Error de Telegram" bot_log.txt >nul
        if not errorlevel 1 goto :checked
    )
)

:checked
if "%success%"=="1" (
    echo.
    echo ==================================================
    echo [+] EL BOT SE HA INICIADO CORRECTAMENTE EN SEGUNDO PLANO
    echo [!] Ya puedes cerrar esta ventana de CMD.
    echo ==================================================
) else (
    echo.
    echo ==================================================
    echo [-] HUBO UN ERROR AL INICIAR EL BOT o tardo demasiado.
    echo [!] Revisa el archivo 'bot_log.txt' para ver los detalles.
    echo ==================================================
)
echo.
pause
