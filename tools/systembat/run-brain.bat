@echo off
rem Chay AI Brain (FastAPI) o cong 8000, tu reload khi sua code.
setlocal
call "%~dp0_env.bat"
title OmniAI - Brain
cd /d "%ROOT%\ai-brain"
if not exist .venv\Scripts\python.exe (
  echo [loi] Chua co venv cho ai-brain. Chay tools\systembat\setup.bat truoc.
  call "%~dp0_pause.bat"
  exit /b 1
)
rem Brain chi chay LLM that va doc catalog tu Postgres that (decisions #25). Thieu cau hinh thi bao ro roi dung.
if "%LLM_API_KEY%"=="" (
  echo [loi] Thieu LLM_API_KEY trong .env. Brain chi chay gemini hoac groq, khong con che do fake.
  call "%~dp0_pause.bat"
  exit /b 1
)
if "%CHATBOT_DB_URL%"=="" (
  echo [loi] Thieu CHATBOT_DB_URL trong .env. Chep 2 dong CHATBOT_DB_* tu .env.example.
  call "%~dp0_pause.bat"
  exit /b 1
)
if "%LLM_PROVIDER%"=="" set "LLM_PROVIDER=gemini"
echo AI Brain: http://localhost:8000/health   (LLM_PROVIDER=%LLM_PROVIDER%)
.venv\Scripts\python -m uvicorn app.main:app --reload --port 8000
