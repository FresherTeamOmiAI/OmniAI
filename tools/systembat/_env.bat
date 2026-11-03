@echo off
rem Helper dung chung, khong chay truc tiep. Cach dung: call "%~dp0_env.bat"
rem - Dat ROOT = thu muc goc repo
rem - Nap bien tu file .env (backend va ai-brain khong tu doc .env)
rem - Bat UTF-8 cho console de log tieng Viet hien dung

for %%I in ("%~dp0..\..") do set "ROOT=%%~fI"
chcp 65001 >nul
set "PYTHONUTF8=1"

if not exist "%ROOT%\.env" (
  echo [canh bao] Chua co file .env, dung gia tri mac dinh. Chay setup.bat de tao.
  goto :eof
)
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT%\.env") do set "%%A=%%B"
goto :eof
