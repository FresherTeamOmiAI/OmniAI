@echo off
rem Chay simulator TikTok Shop o cong 8090 (US-25). Khong can Docker, khong dung DB.
rem Khoa app (TIKTOK_APP_KEY/SECRET) phai trung voi backend: ca hai cung doc tu .env.
setlocal
call "%~dp0_env.bat"
title OmniAI - TikTok Sim
cd /d "%ROOT%\simulators	iktok-shop"
if not defined TIKTOK_SIM_PORT set "TIKTOK_SIM_PORT=8090"
echo TikTok Shop simulator: http://localhost:%TIKTOK_SIM_PORT%/sim/deliveries
call mvn spring-boot:run
