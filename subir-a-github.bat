@echo off
title Subir MicroScan a GitHub
cd /d "%~dp0"
echo ========================================================
echo       SUBIENDO PROYECTO MICROSCAN A GITHUB
echo ========================================================
echo.
echo Repositorio remoto: https://github.com/Luispaca2002/MicroScan.git
echo.
echo Ejecutando: git push -u origin main ...
echo.
git push -u origin main
echo.
if %errorlevel% equ 0 (
    echo ========================================================
    echo  [EXITO] El proyecto se ha subido correctamente a GitHub!
    echo ========================================================
) else (
    echo ========================================================
    echo  [ERROR] Hubo un problema al subir. Revisa los mensajes arriba.
    echo ========================================================
)
echo.
pause
