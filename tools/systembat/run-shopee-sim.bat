@echo off
rem Chay simulator Shopee o cong 8091 (US-21, US-50). Can Postgres (infra-up.bat): tu tao database shopee neu chua co.
rem Khoa test (SHOPEE_PARTNER_ID/KEY, SHOPEE_SHOP_ID, SHOPEE_ACCESS_TOKEN) phai trung voi backend: ca hai cung doc tu .env.
setlocal
call "%~dp0_env.bat"
title OmniAI - Shopee Sim
cd /d "%ROOT%\simulators\shopee"
if not defined SHOPEE_SIM_PORT set "SHOPEE_SIM_PORT=8091"
echo Shopee simulator: http://localhost:%SHOPEE_SIM_PORT%/ui
call mvn spring-boot:run
