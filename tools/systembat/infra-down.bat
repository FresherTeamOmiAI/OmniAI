@echo off
rem Tat ha tang Docker. Du lieu Postgres van giu trong volume pgdata.
rem Muon xoa sach du lieu: infra-down.bat --reset
setlocal
for %%I in ("%~dp0..\..") do set "ROOT=%%~fI"
cd /d "%ROOT%"

if /i "%~1"=="--reset" (
  echo Tat ha tang va XOA volume du lieu Postgres...
  docker compose down -v
) else (
  docker compose down
)
call "%~dp0_pause.bat"
