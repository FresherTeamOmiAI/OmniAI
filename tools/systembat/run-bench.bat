@echo off
rem Chay bench qua API simulator. Can backend + AI Brain dang chay (run-all.bat).
rem Truyen them tham so cho bench.run, vd: run-bench.bat --dataset datasets\x.jsonl
setlocal
call "%~dp0_env.bat"
cd /d "%ROOT%\bench"
if not exist .venv\Scripts\python.exe (
  echo [loi] Chua co venv cho bench. Chay tools\systembat\setup.bat truoc.
  goto fail
)
curl -s -o nul -f http://localhost:8080/actuator/health
if errorlevel 1 (
  echo [loi] Backend chua chay o :8080. Chay run-all.bat truoc.
  goto fail
)
.venv\Scripts\python -m bench.run %*
set "RC=%errorlevel%"
echo Bao cao luu o bench\reports\
call "%~dp0_pause.bat"
exit /b %RC%

:fail
call "%~dp0_pause.bat"
exit /b 1
