@echo off
title Consola del Bot de Desarrollo
set PYTHONUTF8=1
set PYTHONIOENCODING=utf-8
echo ==================================================
echo       INICIANDO BOT DE DESARROLLO EN CONSOLA      
echo ==================================================
echo.

:: Cerrar cualquier instancia previa colgada
echo [INFO] Cerrando instancias previas del bot...
type nul | powershell -NoProfile -NonInteractive -Command "Get-CimInstance Win32_Process -Filter \"CommandLine like '%%telegram_developer_bot.py%%'\" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
type nul | powershell -NoProfile -NonInteractive -Command "Get-CimInstance Win32_Process -Filter \"CommandLine like '%%actualizar.ps1%%'\" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
ping 127.0.0.1 -n 2 >nul

:: Verificar e instalar dependencias
echo [INFO] Verificando e instalando bibliotecas de Python requeridas (requests)...
python -m pip install requests
echo.

:: Iniciar el bot de forma interactiva y visible
echo [INFO] Iniciando el bot. Por favor, deja esta ventana abierta.
echo.
python -u telegram_developer_bot.py

echo.
echo ==================================================
echo [-] EL BOT SE HA DETENIDO.
echo ==================================================
pause
