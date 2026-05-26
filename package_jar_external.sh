#!/usr/bin/env bash
# ============================================================
#  package_jar_external.sh  (macOS / Linux)
#  ----------------------------------------------------------
#  Builds a SLIM "external assets" JAR: the compiled game only,
#  with NO music and NO sfx bundled inside. At runtime the game
#  reads its audio from folders next to the JAR (the working
#  directory):
#
#      <run folder>/music/...
#      <run folder>/sfx/...
#
#  (AppPaths.musicDir() prefers a local music/ folder; the SFX
#   resolver already prefers a local sfx/ folder.)
#
#  This is the cross-platform twin of package_jar_external.ps1.
#  Compare with run.sh, which compiles + runs in place, and with
#  package_jar.ps1 (Windows), which bundles ~1 GB of music INSIDE
#  the JAR and extracts it to the user data dir on first run.
#
#  Usage:
#    ./package_jar_external.sh
#        Builds target/MAB-external.jar. Run it from any
#        folder that has music/ and sfx/ subfolders (e.g. the repo
#        root):
#            java -jar target/MAB-external.jar
#
#    ./package_jar_external.sh --stage
#        Also assembles a ready-to-run folder at target/dist-external/
#        containing the JAR plus copies of music/ and sfx/.
#        (Copies ~1 GB; slower.)
#
#    ./package_jar_external.sh --smoke-test
#        Builds, then headless-launches the JAR from the repo root
#        (where music/ and sfx/ live) to confirm it boots.
#
#  Options:
#    --jar PATH         Output jar path (default target/MAB-external.jar)
#    --stage            Assemble a runnable folder with jar + music + sfx
#    --stage-dir DIR    Stage destination (default target/dist-external)
#    --smoke-test       Boot-check the jar after building
#    -h, --help         Show this help
#
#  Make executable once with:  chmod +x package_jar_external.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---- Defaults ----
JAR_PATH="target/MAB-external.jar"
STAGE=0
STAGE_DIR="target/dist-external"
SMOKE_TEST=0

SRC_DIR="src/main/java"
CLASSES_DIR="target/classes"
MANIFEST_FILE="target/manifest-external.mf"
MAIN_CLASS="com.tetris.Main"

usage() {
    sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

# ---- Parse args ----
while [ $# -gt 0 ]; do
    case "$1" in
        --jar)        JAR_PATH="${2:?--jar needs a path}"; shift 2 ;;
        --stage)      STAGE=1; shift ;;
        --stage-dir)  STAGE_DIR="${2:?--stage-dir needs a path}"; shift 2 ;;
        --smoke-test) SMOKE_TEST=1; shift ;;
        -h|--help)    usage 0 ;;
        *) echo "[ERROR] Unknown option: $1" >&2; usage 1 ;;
    esac
done

# ---- Verify JDK ----
if ! command -v javac >/dev/null 2>&1; then
    echo "[ERROR] javac not found. A full JDK 17+ (not just a JRE) is required." >&2
    exit 1
fi
if ! command -v jar >/dev/null 2>&1; then
    echo "[ERROR] jar not found. A full JDK 17+ is required." >&2
    exit 1
fi
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] java not found on PATH." >&2
    exit 1
fi

# ---- Repo-relative path guard (refuse to delete outside the repo) ----
# Portable: rejects absolute paths and any path containing '..', then treats
# the argument as relative to the repo root. Avoids depending on realpath or
# python3, which are not always present on macOS.
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
mkdir -p "$CLASSES_DIR"
mkdir -p "$(dirname "$JAR_PATH")"

echo "[build] Compiling Java sources..."
SRC_LIST="$(mktemp -t tetris_sources.XXXXXX)"
trap 'rm -f "$SRC_LIST"' EXIT
find "$SRC_DIR" -name '*.java' > "$SRC_LIST"
javac -d "$CLASSES_DIR" -encoding UTF-8 "@$SRC_LIST"

echo "[package] Writing JAR manifest..."
printf 'Manifest-Version: 1.0\nMain-Class: %s\n\n' "$MAIN_CLASS" > "$MANIFEST_FILE"

[ -f "$JAR_PATH" ] && rm -f "$JAR_PATH"

# Slim JAR: compiled classes + manifest, plus the small open-licensed fonts
# (registered at startup). NO music, no sfx, and no music-manifest.txt — so
# RuntimeBootstrap finds nothing to extract and the game reads music/ and
# sfx/ from the working directory.
echo "[package] Creating slim JAR (no bundled audio) $JAR_PATH ..."
JAR_ARGS=(--create --no-compress --file "$JAR_PATH" --manifest "$MANIFEST_FILE"
          -C "$CLASSES_DIR" .)
if [ -d "fonts" ]; then
    JAR_ARGS+=(-C "$SCRIPT_DIR" fonts)
else
    echo "[package] WARNING: fonts/ folder not found; jar will rely on host fonts only." >&2
fi
jar "${JAR_ARGS[@]}"

JAR_SIZE_MIB="$(du -m "$JAR_PATH" | cut -f1)"
echo "[package] Built $JAR_PATH (~${JAR_SIZE_MIB} MiB)"

if [ "$STAGE" -eq 1 ]; then
    echo "[stage] Assembling ready-to-run folder at $STAGE_DIR ..."
    remove_tree_in_repo "$STAGE_DIR"
    mkdir -p "$STAGE_DIR"
    cp "$JAR_PATH" "$STAGE_DIR/$(basename "$JAR_PATH")"
    for asset in music sfx; do
        if [ -d "$asset" ]; then
            echo "[stage]   copying $asset/ ..."
            cp -R "$asset" "$STAGE_DIR/"
        else
            echo "[stage]   WARNING: $asset/ not found in repo; skipping." >&2
        fi
    done
    echo "[stage] Done. Run with:"
    echo "[stage]   cd \"$STAGE_DIR\" && java -jar \"$(basename "$JAR_PATH")\""
fi

if [ "$SMOKE_TEST" -eq 1 ]; then
    if [ ! -d "music" ]; then
        echo "[ERROR] Smoke test needs a music/ folder in the repo root." >&2
        exit 1
    fi
    SMOKE_DIR="$SCRIPT_DIR/target/jar-external-smoke-data"
    remove_tree_in_repo "target/jar-external-smoke-data"
    echo "[smoke] Running external JAR smoke test from repo root..."
    # extractMusic=false: nothing is bundled, so skip the extraction probe and
    # exercise the local music/ + sfx/ resolution path instead.
    java "-Dtetris.appDataDir=$SMOKE_DIR" "-Dtetris.bootstrap.extractMusic=false" -jar "$JAR_PATH" --smoke-test
fi

echo "[done] Slim JAR built. Place music/ and sfx/ next to it (or run from a"
echo "[done] folder that has them), then: java -jar \"$JAR_PATH\""
