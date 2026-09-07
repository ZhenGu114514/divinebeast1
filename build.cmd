@echo off
setlocal
rem ================================================================
rem  Divine & Beast - one-click build (Forge 1.20.1)
rem  Requires: JDK 17+ and Gradle 8.1.1+ on PATH (see INSTALL_CN.md)
rem  Usage: double-click build.cmd  (or: build.cmd nopause  in CI)
rem ================================================================

if /i "%~1"=="nopause" set NOPAUSE=1

echo ============================================
echo   Divine ^& Beast  -  one-click build
echo   Minecraft 1.20.1  /  Forge
echo ============================================

rem ---- 1) check java ----
where java >nul 2>nul
if errorlevel 1 goto :nojava

rem ---- 1b) reject Java 8 (JRE) ----
java -version 2>&1 | findstr /C:"1.8." >nul
if not errorlevel 1 goto :oldjava

rem ---- 2) pick build launcher (gradlew first, else system gradle) ----
set BUILDER=gradle
if exist "gradlew.bat" set BUILDER=gradlew.bat
where gradle >nul 2>nul
if errorlevel 1 (
  if not exist "gradlew.bat" goto :nogradle
)

echo [1/2] building with: %BUILDER%
call %BUILDER% build
if errorlevel 1 goto :buildfail

echo.
echo [2/2] build finished. Latest jar:
for /f "delims=" %%F in ('dir /b /o-d "build\libs\divinebeast-*.jar" 2^>nul') do (
    echo   %CD%\build\libs\%%F
)
echo.
echo Put the jar (and Curios API 1.20.1) into your Forge instance's mods\ folder.
echo Details: see INSTALL_CN.md
goto :end

:nojava
echo [ERROR] Java not found on PATH.
echo         Install a JDK 17 or newer, e.g. Eclipse Temurin:
echo         https://adoptium.net/
echo         then re-run this script.
goto :fail

:oldjava
echo [ERROR] Java 8 detected. Minecraft Forge 1.20.1 needs a JDK 17.
echo         Set JAVA_HOME to a JDK 17, e.g.:
echo           set JAVA_HOME=C:\Program Files\Java\jdk-17
echo         then re-run this script.
goto :fail

:nogradle
echo [ERROR] Gradle not found and no gradlew wrapper present in this folder.
echo         Install Gradle 8.1.1: https://services.gradle.org/distributions/gradle-8.1.1-bin.zip
echo         (unzip, add bin\ to PATH) and re-run this script.
goto :fail

:buildfail
echo [ERROR] Gradle build failed. Check the messages above, or open build\reports\...
echo         If it mentions downloads, a network connection is required on first run.
goto :fail

:fail
echo.
if defined NOPAUSE exit /b 1
pause
exit /b 1

:end
if defined NOPAUSE exit /b 0
pause
exit /b 0
