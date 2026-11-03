@echo off
rem Bat ha tang Docker (Postgres + pgvector, Redis, RabbitMQ) va doi den khi healthy.
setlocal
for %%I in ("%~dp0..\..") do set "ROOT=%%~fI"
cd /d "%ROOT%"

docker info >nul 2>&1
if errorlevel 1 (
  echo [loi] Docker Desktop chua chay. Mo Docker Desktop roi chay lai.
  goto fail
)
echo Dang bat ha tang Docker...
docker compose up -d --wait
if errorlevel 1 (
  echo [loi] Khong khoi dong duoc ha tang. Xem log: docker compose logs
  goto fail
)
echo Ha tang san sang: Postgres :5432, Redis :6379, RabbitMQ :5672 (UI http://localhost:15672)
call "%~dp0_pause.bat"
exit /b 0

:fail
call "%~dp0_pause.bat"
exit /b 1
