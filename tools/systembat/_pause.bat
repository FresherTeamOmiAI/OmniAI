@echo off
rem Helper: dung man hinh truoc khi dong neu script duoc mo bang double-click (de kip doc ket qua).
rem Khong dung khi chay tu terminal, hoac khi duoc goi long trong script khac (OMNI_NESTED).
if defined OMNI_NESTED goto :eof
echo %cmdcmdline% | find /i "/c" >nul && pause
goto :eof
