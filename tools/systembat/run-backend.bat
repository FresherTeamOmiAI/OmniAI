@echo off
rem Chay backend Spring Boot o cong 8080. Can ha tang Docker dang chay (infra-up.bat).
setlocal
call "%~dp0_env.bat"
title OmniAI - Backend
cd /d "%ROOT%\backend"
docker compose -f "%ROOT%\docker-compose.yml" ps --status running postgres 2>nul | find "postgres" >nul
if errorlevel 1 (
  echo [canh bao] Postgres chua chay - backend se loi ket noi DB. Chay infra-up.bat truoc.
)
echo Backend: http://localhost:8080/actuator/health
call mvn spring-boot:run
