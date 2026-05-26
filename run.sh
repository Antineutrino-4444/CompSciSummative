#!/usr/bin/env bash
# ============================================================
#  run.sh — Launcher for MAB (Linux / macOS)
# ============================================================
#  Always performs a clean rebuild, then runs the game.
#  Requires: a JDK 17+ on PATH (provides javac/java).
#
#  Any extra arguments are passed through to the game,
#  e.g.  ./run.sh 5   to start at level 5.
#
#  Make executable once with:  chmod +x run.sh
# ============================================================

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SRC_DIR="src/main/java"
OUT_DIR="target/classes"
MAIN_CLASS="com.tetris.Main"
SRC_LIST="$(mktemp -t tetris_sources.XXXXXX)"

pause_exit() {
    if [ -t 0 ]; then
        echo
        read -r -p "Press Enter to close..." _
    fi
    exit "${1:-0}"
}

# ---- Verify JDK is available ----
if ! command -v javac >/dev/null 2>&1; then
    echo "[ERROR] javac not found. A full JDK 17+ (not just a JRE) is required."
    pause_exit 1
fi
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] java not found on PATH."
    pause_exit 1
fi

# ---- Bundled font check ----
# Checks the font files the game registers from ./fonts at startup.
# Missing fonts are cosmetic; the game falls back to JVM logical fonts.
echo "[fonts] Checking bundled fonts..."
FONTS_MISSING=0

check_font_file() {
    if [ -f "fonts/$1" ]; then
        printf "[fonts]   [OK]      %s\n" "$1"
    else
        printf "[fonts]   [MISSING] %s\n" "$1"
        FONTS_MISSING=$((FONTS_MISSING + 1))
    fi
}

for font_file in \
    Bahnschrift.ttf \
    FranklinGothic.ttf \
    SegoeUI-Regular.ttf \
    SegoeUI-Bold.ttf \
    HelveticaNeue-Roman.otf \
    HelveticaNeue-Bold.ttf \
    Ubuntu-Regular.ttf \
    Ubuntu-Bold.ttf \
    Consolas-Regular.ttf \
    Consolas-Bold.ttf \
    LucidaConsole.ttf \
    UbuntuMono-Regular.ttf \
    UbuntuMono-Bold.ttf
do
    check_font_file "$font_file"
done

if [ "$FONTS_MISSING" -gt 0 ]; then
    echo "[fonts] No download or package install will be attempted."
    echo "[fonts] The game will fall back to JVM logical fonts for any missing bundled file."
else
    echo "[fonts] Bundled font check finished."
fi
echo

# ---- Clean previous build ----
echo "[clean] Removing $OUT_DIR ..."
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# ---- Compile every .java under src/main/java ----
echo "[build] Compiling sources..."
find "$SRC_DIR" -type f -name '*.java' > "$SRC_LIST"
javac -d "$OUT_DIR" -encoding UTF-8 "@$SRC_LIST"
JAVAC_RC=$?
rm -f "$SRC_LIST"
if [ "$JAVAC_RC" -ne 0 ]; then
    echo "[ERROR] Compilation failed."
    pause_exit 1
fi

# ---- Launch ----
echo "[run] Starting MAB..."
java -cp "$OUT_DIR" "$MAIN_CLASS" "$@"
EXIT=$?

if [ "$EXIT" -ne 0 ]; then
    echo
    echo "[exit] Game exited with code $EXIT."
    pause_exit "$EXIT"
fi

exit "$EXIT"
