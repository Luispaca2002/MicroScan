@echo off
chcp 65001 > nul
echo ========================================================
echo     Iniciando Servidor BioScanLab + Tunel Global
echo ========================================================
echo.

echo [1/2] Iniciando backend Ktor en segundo plano...
start "BioScanLab Backend (Puerto 8080)" cmd /k ".\gradlew.bat :server:run"

echo Esperando 4 segundos a que inicie el servidor...
timeout /t 4 /nobreak > nul

echo [2/2] Iniciando túnel seguro de Cloudflare...
echo.
echo ========================================================
echo Busca abajo la linea con la direccion https://...trycloudflare.com
echo Esa es tu direccion publica para usar desde cualquier celular e internet.
echo ========================================================
echo.

".\tools\cloudflared.exe" tunnel --url http://localhost:8080
