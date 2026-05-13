@echo off
REM ============================================================
REM  run.bat -- Launcher for Modern Tetris
REM ============================================================
REM  Always performs a clean rebuild, then runs the game.
REM  Requires: a JDK 17+ on PATH (provides javac/java).
REM
REM  Any extra arguments are passed through to the game,
REM  e.g.  run.bat 5   to start at level 5.
REM ============================================================

setlocal enabledelayedexpansion
cd /d "%~dp0"

set "SRC_DIR=src\main\java"
set "OUT_DIR=target\classes"
set "MAIN_CLASS=com.tetris.Main"
set "SRC_LIST=%TEMP%\tetris_sources.txt"
set "FONT_DIR=C:\Windows\Fonts"

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

REM ---- Font check ----
REM  Checks every font in the game's pickFont priority chains.
REM  Missing fonts are cosmetic -- the game always has a JVM fallback.
echo [fonts] Checking required fonts...
set "FONTS_MISSING=0"

REM  Stencil/headline: Bahnschrift -> Franklin Gothic Med -> Segoe UI
call :chkfont "Bahnschrift          " "%FONT_DIR%\Bahnschrift.TTF"
call :chkfont "Franklin Gothic Med. " "%FONT_DIR%\framd.ttf"
call :chkfont "Segoe UI             " "%FONT_DIR%\segoeui.ttf"
REM  Terminal/monospace: Consolas -> Lucida Console
call :chkfont "Consolas             " "%FONT_DIR%\consola.ttf"
call :chkfont "Lucida Console       " "%FONT_DIR%\lucon.ttf"
REM  Broad fallbacks expected on all Windows installs
call :chkfont "Arial                " "%FONT_DIR%\arial.ttf"
call :chkfont "Courier New          " "%FONT_DIR%\cour.ttf"
call :chkfont "Tahoma               " "%FONT_DIR%\tahoma.ttf"
call :chkfont "Verdana              " "%FONT_DIR%\verdana.ttf"

if "!FONTS_MISSING!"=="0" (
    echo [fonts] All fonts present.
) else (
    echo [fonts] !FONTS_MISSING! font^(s^) missing -- cosmetic only, game uses JVM fallbacks.
    echo [fonts] To restore: Settings ^> System ^> Optional Features ^> Add a feature ^> Fonts.
)
echo.
goto :fonts_done

:chkfont
if exist "%~2" (
    echo [fonts]   [OK]      %~1
) else (
    echo [fonts]   [MISSING] %~1
    set /a FONTS_MISSING+=1
)
exit /b 0

:fonts_done

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
