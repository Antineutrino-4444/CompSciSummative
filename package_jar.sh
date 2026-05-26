#!/usr/bin/env bash
# ============================================================
#  package_jar.sh  (macOS / Linux)
#  ----------------------------------------------------------
#  Builds a SELF-CONTAINED "bundled" JAR: the compiled game with
#  the entire music/ library embedded INSIDE the jar, plus a
#  music-manifest.txt. On first run RuntimeBootstrap extracts the
#  bundled tracks to the per-user data dir, e.g.:
#
#      macOS : ~/Library/Application Support/MAB/music/
#      Linux : ~/.local/share/mab/music/
#
#  This is the cross-platform twin of package_jar.ps1 (Windows).
#  For the slim, audio-beside-the-jar variant, use
#  package_jar_external.sh instead.
#
#  Usage:
#    ./package_jar.sh
#        Builds target/MAB.jar (music bundled in).
#            java -jar target/MAB.jar
#
#    ./package_jar.sh --no-music
#        Code-only diagnostic jar (no music bundled, no manifest).
#
#    ./package_jar.sh --smoke-test
#        Builds, then headless-launches to confirm it boots and
#        extracts bundled music.
#
#  Options:
#    --jar PATH      Output jar path (default target/MAB.jar)
#    --no-music      Skip bundling music (diagnostic only)
#    --smoke-test    Boot-check the jar after building
#    -h, --help      Show this help
#
#  Make executable once with:  chmod +x package_jar.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---- Defaults ----
JAR_PATH="target/MAB.jar"
INCLUDE_MUSIC=1
SMOKE_TEST=0

SRC_DIR="src/main/java"
CLASSES_DIR="target/classes"
PACKAGE_DIR="target/package-resources"
MANIFEST_FILE="target/manifest.mf"
MAIN_CLASS="com.tetris.Main"

usage() {
    sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

# ---- Parse args ----
while [ $# -gt 0 ]; do
    case "$1" in
        --jar)        JAR_PATH="${2:?--jar needs a path}"; shift 2 ;;
        --no-music)   INCLUDE_MUSIC=0; shift ;;
        --smoke-test) SMOKE_TEST=1; shift ;;
        -h|--help)    usage 0 ;;
        *) echo "[ERROR] Unknown option: $1" >&2; usage 1 ;;
    esac
done

# ---- Verify JDK ----
for tool in javac jar java; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[ERROR] $tool not found. A full JDK 17+ (not just a JRE) is required." >&2
        exit 1
    fi
done

# ---- Repo-relative path guard (refuse to delete outside the repo) ----
remove_tree_in_repo() {
    local rel="$1"
    case "$rel" in
        /*)    echo "[ERROR] Refusing to remove absolute path: $rel" >&2; exit 1 ;;
        *..*)  echo "[ERROR] Refusing to remove path containing '..': $rel" >&2; exit 1 ;;
    esac
    local full="$SCRIPT_DIR/$rel"
    [ -e "$full" ] && rm -rf "$full"
    return 0
}

echo "[clean] Preparing target directories..."
remove_tree_in_repo "$CLASSES_DIR"
remove_tree_in_repo "$PACKAGE_DIR"
mkdir -p "$CLASSES_DIR"
mkdir -p "$PACKAGE_DIR"
mkdir -p "$(dirname "$JAR_PATH")"

echo "[build] Compiling Java sources..."
SRC_LIST="$(mktemp -t tetris_sources.XXXXXX)"
trap 'rm -f "$SRC_LIST"' EXIT
find "$SRC_DIR" -name '*.java' > "$SRC_LIST"
javac -d "$CLASSES_DIR" -encoding UTF-8 "@$SRC_LIST"

echo "[package] Writing JAR manifest..."
printf 'Manifest-Version: 1.0\nMain-Class: %s\n\n' "$MAIN_CLASS" > "$MANIFEST_FILE"

if [ "$INCLUDE_MUSIC" -eq 1 ]; then
    if [ ! -d "music" ]; then
        echo "[ERROR] music/ folder not found. Use --no-music only for code-only diagnostics." >&2
        exit 1
    fi
    echo "[package] Writing packaged music manifest..."
    # Each row: <relative/path/with/forward/slashes><TAB><size-in-bytes>.
    # RuntimeBootstrap reads this from inside the jar and extracts each
    # listed resource to the user data dir on first run. wc -c is the
    # portable byte-count (stat flags differ between macOS and Linux).
    : > "$PACKAGE_DIR/music-manifest.txt"
    while IFS= read -r f; do
        rel="${f#./}"
        size="$(wc -c < "$f" | tr -d '[:space:]')"
        printf '%s\t%s\n' "$rel" "$size" >> "$PACKAGE_DIR/music-manifest.txt"
    done < <(find music -name '*.wav' -type f | LC_ALL=C sort)
else
    echo "[package] Skipping bundled music by request."
fi

[ -f "$JAR_PATH" ] && rm -f "$JAR_PATH"

echo "[package] Creating $JAR_PATH ..."
JAR_ARGS=(--create --no-compress --file "$JAR_PATH" --manifest "$MANIFEST_FILE"
          -C "$CLASSES_DIR" .
          -C "$PACKAGE_DIR" .)
# Bundle the open-licensed fonts (registered at startup so the UI font
# chains resolve on hosts lacking the proprietary originals).
if [ -d "fonts" ]; then
    JAR_ARGS+=(-C "$SCRIPT_DIR" fonts)
else
    echo "[package] WARNING: fonts/ folder not found; jar will rely on host fonts only." >&2
fi
if [ "$INCLUDE_MUSIC" -eq 1 ]; then
    JAR_ARGS+=(-C "$SCRIPT_DIR" music)
fi
jar "${JAR_ARGS[@]}"

JAR_SIZE_MIB="$(du -m "$JAR_PATH" | cut -f1)"
echo "[package] Built $JAR_PATH (~${JAR_SIZE_MIB} MiB)"

if [ "$SMOKE_TEST" -eq 1 ]; then
    SMOKE_DIR="$SCRIPT_DIR/target/jar-smoke-data"
    remove_tree_in_repo "target/jar-smoke-data"
    echo "[smoke] Running executable JAR smoke test..."
    java "-Dtetris.appDataDir=$SMOKE_DIR" -jar "$JAR_PATH" --smoke-test
fi

echo "[done] Run with: java -jar \"$JAR_PATH\""
