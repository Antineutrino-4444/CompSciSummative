#!/usr/bin/env bash
# ============================================================
#  run.sh — Launcher for Modern Tetris (Linux / macOS)
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

# ---- Font check ----
# Checks every font in the game's pickFont priority chains.
# Missing fonts are cosmetic — the game always falls back to the JVM default.
echo "[fonts] Checking required fonts..."
OS_TYPE="$(uname -s)"
FONTS_MISSING=0

# fc-list output format: /path/to/font.ttf: Family Name:style=Style
# We match case-insensitively against the family name field.
font_present() {
    # $1 = family name substring to match
    if ! command -v fc-list >/dev/null 2>&1; then return 1; fi
    fc-list | grep -qi "$1"
}

report() {
    # $1 = display name,  $2 = fc-list search key
    if font_present "$2"; then
        printf "[fonts]   [OK]      %s\n" "$1"
    else
        printf "[fonts]   [MISSING] %s\n" "$1"
        FONTS_MISSING=$((FONTS_MISSING + 1))
    fi
}

if command -v fc-list >/dev/null 2>&1; then
    # Stencil / headline fonts  (preferred: Bahnschrift → Franklin Gothic → Segoe UI
    #                             → Helvetica Neue → Ubuntu → Liberation Sans)
    report "Bahnschrift"           "Bahnschrift"
    report "Franklin Gothic Med."  "Franklin Gothic"
    report "Segoe UI"              "Segoe UI"
    report "Helvetica Neue"        "Helvetica Neue"
    report "Ubuntu"                "Ubuntu"
    report "Liberation Sans"       "Liberation Sans"
    # Terminal / monospace fonts  (preferred: Consolas → Lucida Console)
    report "Consolas"              "Consolas"
    report "Lucida Console"        "Lucida Console"
    # Broad fallbacks expected on most systems
    report "DejaVu Sans"           "DejaVu Sans"
    report "Noto Sans"             "Noto Sans"
    report "FreeSans"              "FreeSans"
    report "Arial"                 "Arial"
    report "Helvetica"             "Helvetica"
    report "Courier New"           "Courier New"
    report "Tahoma"                "Tahoma"
    report "Verdana"               "Verdana"
else
    echo "[fonts]   fc-list not available; skipping font check."
fi

if [ "$FONTS_MISSING" -gt 0 ]; then
    echo "[fonts] $FONTS_MISSING font(s) missing. Attempting to install..."
    if [ "$OS_TYPE" = "Darwin" ]; then
        # macOS ships Helvetica/Helvetica Neue; nothing critical to install.
        echo "[fonts]   macOS: built-in system fonts are sufficient."
    elif command -v apt-get >/dev/null 2>&1; then
        echo "[fonts]   apt: installing fonts-urw-base35 fonts-liberation fonts-noto..."
        sudo apt-get install -y fonts-urw-base35 fonts-liberation fonts-noto 2>/dev/null \
            && { command -v fc-cache >/dev/null 2>&1 && fc-cache -f; } \
            && echo "[fonts]   Done." \
            || echo "[fonts]   Install skipped or failed; game uses JVM fallback fonts."
    elif command -v dnf >/dev/null 2>&1; then
        echo "[fonts]   dnf: installing urw-fonts liberation-fonts google-noto-sans-fonts..."
        sudo dnf install -y urw-fonts liberation-fonts google-noto-sans-fonts 2>/dev/null \
            && { command -v fc-cache >/dev/null 2>&1 && fc-cache -f; } \
            && echo "[fonts]   Done." \
            || echo "[fonts]   Install skipped or failed; game uses JVM fallback fonts."
    elif command -v pacman >/dev/null 2>&1; then
        echo "[fonts]   pacman: installing ttf-liberation noto-fonts..."
        sudo pacman -S --noconfirm ttf-liberation noto-fonts 2>/dev/null \
            && echo "[fonts]   Done." \
            || echo "[fonts]   Install skipped or failed; game uses JVM fallback fonts."
    elif command -v zypper >/dev/null 2>&1; then
        echo "[fonts]   zypper: installing liberation-fonts..."
        sudo zypper install -y liberation-fonts 2>/dev/null \
            && echo "[fonts]   Done." \
            || echo "[fonts]   Install skipped or failed; game uses JVM fallback fonts."
    else
        echo "[fonts]   Unknown package manager — install fonts manually if needed."
        echo "[fonts]   Game will use JVM fallback fonts (cosmetic only)."
    fi
else
    echo "[fonts] All fonts present."
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
echo "[run] Starting Modern Tetris..."
java -cp "$OUT_DIR" "$MAIN_CLASS" "$@"
EXIT=$?

if [ "$EXIT" -ne 0 ]; then
    echo
    echo "[exit] Game exited with code $EXIT."
    pause_exit "$EXIT"
fi

exit "$EXIT"
