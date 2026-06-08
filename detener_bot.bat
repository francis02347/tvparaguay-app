@echo off
title Detener Bot de Desarrollo
echo ==================================================
echo       DETENIENDO BOT DE DESARROLLO REMOTO         
echo ==================================================
echo.
echo Buscando y finalizando el proceso del bot de segundo plano...
echo.

type nul | powershell -NoProfile -NonInteractive -Command "Get-CimInstance Win32_Process -Filter \"CommandLine like '%%telegram_developer_bot.py%%'\" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
type nul | powershell -NoProfile -NonInteractive -Command "Get-CimInstance Win32_Process -Filter \"CommandLine like '%%actualizar.ps1%%'\" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"

echo.
echo ==================================================
echo [+] EL BOT SE HA DETENIDO CORRECTAMENTE.
echo [!] Ya puedes cerrar esta ventana de CMD.
echo ==================================================
echo.
pause
