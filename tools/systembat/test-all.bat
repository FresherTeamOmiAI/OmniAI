@echo off
rem Chay toan bo test: backend (mvn test, can Docker cho Testcontainers), ai-brain pytest, bench pytest, web build.
rem In bang PASS/FAIL cuoi cung; exit code 1 neu co phan FAIL.
setlocal
call "%~dp0_env.bat"
set "FAILED="

echo === [1/4] backend: mvn test ===
docker info >nul 2>&1
if errorlevel 1 (
  echo [bo qua] Docker Desktop chua chay - Testcontainers can Docker.
  set "R_BACKEND=SKIP (Docker chua chay)"
  set "FAILED=1"
) else (
  cd /d "%ROOT%\backend"
  call mvn -q test
  if errorlevel 1 (set "R_BACKEND=FAIL" & set "FAILED=1") else (set "R_BACKEND=PASS")
)

echo === [2/4] ai-brain: pytest ===
cd /d "%ROOT%\ai-brain"
.venv\Scripts\python -m pytest -q
if errorlevel 1 (set "R_BRAIN=FAIL" & set "FAILED=1") else (set "R_BRAIN=PASS")

echo === [3/4] bench: pytest ===
cd /d "%ROOT%\bench"
.venv\Scripts\python -m pytest -q
if errorlevel 1 (set "R_BENCH=FAIL" & set "FAILED=1") else (set "R_BENCH=PASS")

echo === [4/4] web: npm run build ===
cd /d "%ROOT%\web"
call npm run build
if errorlevel 1 (set "R_WEB=FAIL" & set "FAILED=1") else (set "R_WEB=PASS")

echo.
echo ========= KET QUA =========
echo   backend  : %R_BACKEND%
echo   ai-brain : %R_BRAIN%
echo   bench    : %R_BENCH%
echo   web      : %R_WEB%
echo ===========================
call "%~dp0_pause.bat"
if defined FAILED exit /b 1
exit /b 0
