@echo off
chcp 65001 >nul
rem 우리집 재생목록 로컬 서버 (http://localhost:8765)
rem Google 로그인은 승인된 원본에 등록된 이 포트(8765)에서만 동작한다
cd /d "%~dp0"
set PY=python
where python >nul 2>nul || set PY=py
where %PY% >nul 2>nul || (echo Python이 설치되어 있지 않습니다. https://www.python.org 에서 설치하세요. & pause & exit /b 1)
start "" http://localhost:8765/
echo 서버 실행 중: http://localhost:8765/   (이 창을 닫으면 서버가 종료됩니다)
%PY% -m http.server 8765 --bind 127.0.0.1
pause
