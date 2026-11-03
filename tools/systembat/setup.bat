@echo off
rem Cai dat lan dau (chay lai nhieu lan van an toan):
rem kiem tra cong cu, tao .env, venv + pip cho ai-brain va bench, npm install cho web.
setlocal
for %%I in ("%~dp0..\..") do set "ROOT=%%~fI"
cd /d "%ROOT%"

echo === [1/5] Kiem tra cong cu ===
set "MISSING="
for %%C in (java mvn python node npm docker) do (
  where %%C >nul 2>&1 || (echo   [thieu] %%C & set "MISSING=1")
)
if defined MISSING (
  echo Can: JDK 21, Maven 3.9+, Python 3.11+, Node.js 20.19+, Docker Desktop. Cai roi chay lai.
  goto fail
)
docker info >nul 2>&1 || echo   [canh bao] Docker Desktop chua chay - can bat truoc khi chay app va test backend.
echo   OK

echo === [2/5] File .env ===
if exist .env (
  echo   Da co .env, giu nguyen.
) else (
  copy /y .env.example .env >nul && echo   Da tao .env tu .env.example
)

echo === [3/5] ai-brain: venv + pip install ===
cd /d "%ROOT%\ai-brain"
if not exist .venv\Scripts\python.exe python -m venv .venv || goto fail
.venv\Scripts\python -m pip install -q --disable-pip-version-check -r requirements.txt || goto fail

echo === [4/5] bench: venv + pip install ===
cd /d "%ROOT%\bench"
if not exist .venv\Scripts\python.exe python -m venv .venv || goto fail
.venv\Scripts\python -m pip install -q --disable-pip-version-check -r requirements.txt || goto fail

echo === [5/5] web: npm install ===
cd /d "%ROOT%\web"
call npm install --no-fund --no-audit || goto fail

echo.
echo Xong. Chay toan bo he thong: tools\systembat\run-all.bat
call "%~dp0_pause.bat"
exit /b 0

:fail
echo.
echo [loi] Setup that bai o buoc tren.
call "%~dp0_pause.bat"
exit /b 1
