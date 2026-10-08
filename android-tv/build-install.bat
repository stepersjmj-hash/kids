@echo off
chcp 65001 >nul
rem 우리집 재생목록 TV 앱 빌드 + 크롬캐스트 설치
rem 사용법: build-install.bat 192.168.22.245:포트   (포트는 TV 설정 > 개발자 옵션 > 무선 디버깅 화면의 "IP 주소 및 포트")
cd /d "%~dp0"
set JAVA_HOME=%USERPROFILE%\Android\jdk17
set ADB=%USERPROFILE%\Android\Sdk\platform-tools\adb.exe
call gradlew.bat assembleRelease -q || (echo 빌드 실패 & pause & exit /b 1)
echo 빌드 완료: app\build\outputs\apk\release\app-release.apk
if "%~1"=="" (echo TV 주소를 인자로 주면 설치까지 합니다. & pause & exit /b 0)
"%ADB%" connect %1
"%ADB%" -s %1 install -r app\build\outputs\apk\release\app-release.apk
pause
