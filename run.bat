@echo off
REM ============================================================
REM  run.bat -- Launcher for MAB
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

REM ---- Bundled font check ----
REM  The game registers these fonts from .\fonts at startup.
REM  Missing files are cosmetic -- the game still has JVM logical fallbacks.
echo [fonts] Checking bundled fonts...
set "FONTS_MISSING=0"

call :chkfont "Bahnschrift.ttf      " "fonts\Bahnschrift.ttf"
call :chkfont "FranklinGothic.ttf   " "fonts\FranklinGothic.ttf"
call :chkfont "SegoeUI-Regular.ttf  " "fonts\SegoeUI-Regular.ttf"
call :chkfont "SegoeUI-Bold.ttf     " "fonts\SegoeUI-Bold.ttf"
call :chkfont "HelveticaNeue-Roman  " "fonts\HelveticaNeue-Roman.otf"
call :chkfont "HelveticaNeue-Bold   " "fonts\HelveticaNeue-Bold.ttf"
call :chkfont "Ubuntu-Regular.ttf   " "fonts\Ubuntu-Regular.ttf"
call :chkfont "Ubuntu-Bold.ttf      " "fonts\Ubuntu-Bold.ttf"
call :chkfont "Consolas-Regular.ttf " "fonts\Consolas-Regular.ttf"
call :chkfont "Consolas-Bold.ttf    " "fonts\Consolas-Bold.ttf"
call :chkfont "LucidaConsole.ttf    " "fonts\LucidaConsole.ttf"
call :chkfont "UbuntuMono-Regular   " "fonts\UbuntuMono-Regular.ttf"
call :chkfont "UbuntuMono-Bold.ttf  " "fonts\UbuntuMono-Bold.ttf"

if "!FONTS_MISSING!"=="0" (
    echo [fonts] Bundled fonts present.
) else (
    echo [fonts] !FONTS_MISSING! bundled font file^(s^) missing -- cosmetic only, game uses JVM fallbacks.
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
echo [run] Starting MAB...
java -cp "%OUT_DIR%" %MAIN_CLASS% %*
set "EXIT=%errorlevel%"

if not "%EXIT%"=="0" (
    echo.
    echo [exit] Game exited with code %EXIT%.
    pause
)

endlocal & exit /b %EXIT%
