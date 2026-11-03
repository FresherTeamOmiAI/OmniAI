@echo off
rem Chay toan bo: ha tang Docker -> AI Brain, Backend, Web (moi phan 1 cua so) -> mo trinh duyet.
rem Tat het: stop-all.bat
setlocal
set "HERE=%~dp0"
set "OMNI_NESTED=1"

call "%HERE%infra-up.bat" || goto fail

start "OmniAI - Brain" cmd /k ""%HERE%run-brain.bat""
start "OmniAI - Backend" cmd /k ""%HERE%run-backend.bat""
start "OmniAI - Web" cmd /k ""%HERE%run-web.bat""

echo Dang doi backend khoi dong (toi da 120 giay)...
for /l %%i in (1,1,60) do (
  curl -s -o nul -f http://localhost:8080/actuator/health && goto ready
  timeout /t 2 /nobreak >nul
)
echo [canh bao] Backend chua san sang sau 120 giay - xem cua so "OmniAI - Backend".
goto open

:ready
echo Backend da san sang.

:open
start "" http://localhost:5173
echo.
echo   Web      http://localhost:5173
echo   Policy   http://localhost:5173/admin/policy
echo   Backend  http://localhost:8080/actuator/health
echo   Brain    http://localhost:8000/health
set "OMNI_NESTED="
call "%HERE%_pause.bat"
exit /b 0

:fail
set "OMNI_NESTED="
call "%HERE%_pause.bat"
exit /b 1
