@echo off
REM ============================================================
REM  run.bat — Launcher for Modern Tetris
REM ============================================================
REM  Always performs a clean rebuild, then runs the game.
REM  Requires: a JDK 17+ on PATH (provides javac/java).
REM
REM  Any extra arguments are passed through to the game,
REM  e.g.  run.bat 5   to start at level 5.
REM ============================================================

setlocal
cd /d "%~dp0"

set "SRC_DIR=src\main\java"
set "OUT_DIR=target\classes"
set "MAIN_CLASS=com.tetris.Main"
set "SRC_LIST=%TEMP%\tetris_sources.txt"

REM ---- Verify JDK is available ----
where javac >nul 2>nul
if errorlevel 1 (
    echo [ERROR] javac not found. A full JDK 17+ ^(not just a JRE^) is required.
    pause
    exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] java not found on PATH.
    pause
    exit /b 1
)

REM ---- Clean previous build ----
echo [clean] Removing %OUT_DIR% ...
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"

REM ---- Compile every .java under src\main\java ----
echo [build] Compiling sources...
dir /s /b "%SRC_DIR%\*.java" > "%SRC_LIST%"
javac -d "%OUT_DIR%" -encoding UTF-8 @"%SRC_LIST%"
set "JAVAC_RC=%errorlevel%"
del "%SRC_LIST%" >nul 2>nul
if not "%JAVAC_RC%"=="0" (
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)

REM ---- Launch ----
echo [run] Starting Modern Tetris...
java -cp "%OUT_DIR%" %MAIN_CLASS% %*
set "EXIT=%errorlevel%"

if not "%EXIT%"=="0" (
    echo.
    echo [exit] Game exited with code %EXIT%.
    pause
)

endlocal & exit /b %EXIT%
