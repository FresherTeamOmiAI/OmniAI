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
echo AI Brain: http://localhost:8000/health   (LLM_PROVIDER=%LLM_PROVIDER%)
.venv\Scripts\python -m uvicorn app.main:app --reload --port 8000
