@echo off
rem Chay web (Vite dev server) o cong 5173. /api va /ws duoc proxy sang backend :8080.
setlocal
call "%~dp0_env.bat"
title OmniAI - Web
cd /d "%ROOT%\web"
if not exist node_modules (
  echo Chua co node_modules, dang chay npm install...
  call npm install --no-fund --no-audit || exit /b 1
)
echo Web: http://localhost:5173   Thu luat kenh: http://localhost:5173/admin/policy
call npm run dev
