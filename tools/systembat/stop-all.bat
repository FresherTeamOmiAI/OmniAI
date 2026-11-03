@echo off
rem Tat AI Brain, Backend, Web (theo cong 8000, 8080, 5173 va theo tieu de cua so "OmniAI - ...").
rem Sau do hoi co tat ha tang Docker khong (mac dinh: khong, sau 10 giay).
setlocal
set "HERE=%~dp0"

for %%P in (8000 8080 5173) do (
  for /f "tokens=5" %%I in ('netstat -ano ^| findstr /r /c:":%%P .*LISTENING"') do (
    taskkill /pid %%I /t /f >nul 2>&1 && echo Da tat tien trinh %%I o cong %%P
  )
)
taskkill /fi "WINDOWTITLE eq OmniAI - *" /t /f >nul 2>&1

choice /c yn /t 10 /d n /m "Tat luon ha tang Docker (Postgres, Redis, RabbitMQ)"
if errorlevel 2 goto done
set "OMNI_NESTED=1"
call "%HERE%infra-down.bat"
set "OMNI_NESTED="

:done
echo Da tat.
call "%HERE%_pause.bat"
