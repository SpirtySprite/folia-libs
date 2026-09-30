#!/usr/bin/env bash
# Starts a real Paper or Folia server with the integration plugin installed and checks the result.
#
#   integration/scripts/run-server-test.sh <paper|folia> <minecraft-version> [plugin-jar]
#
# Example: integration/scripts/run-server-test.sh folia 1.21.4
#
# The server jar is downloaded from PaperMC (https://fill.papermc.io) and cached in
# ~/.cache/folia-libs. The plugin jar defaults to integration/target/folia-integration.jar, which
# `mvn -Pintegration package -DskipTests -pl integration -am` builds.
#
# Needs: bash, curl, python3, and a Java that the chosen Minecraft version supports (21 for 1.20.5 - 1.21.x).
set -euo pipefail

if [ $# -lt 2 ]; then
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi

PROJECT="$1"
VERSION="$2"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PLUGIN="${3:-$ROOT/integration/target/folia-integration.jar}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-420}"
CACHE="${FOLIA_LIBS_CACHE:-$HOME/.cache/folia-libs}"

case "$PROJECT" in
  paper) EXPECT_FOLIA=false ;;
  folia) EXPECT_FOLIA=true ;;
  *) echo "The server must be 'paper' or 'folia', not '$PROJECT'." >&2; exit 2 ;;
esac

if [ ! -f "$PLUGIN" ]; then
  echo "Plugin jar not found: $PLUGIN" >&2
  echo "Build it with: mvn -Pintegration package -DskipTests -pl integration -am" >&2
  exit 2
fi

mkdir -p "$CACHE"

# Ask PaperMC for the newest build of this version, and its download URL and checksum.
API="https://fill.papermc.io/v3/projects/$PROJECT/versions/$VERSION/builds/latest"
BUILD_JSON="$(curl -fsS --retry 4 --retry-delay 3 -m 60 "$API")" || {
  echo "Could not fetch $API. Does $PROJECT $VERSION exist?" >&2
  exit 1
}
read -r JAR_NAME JAR_URL JAR_SHA < <(python3 - "$BUILD_JSON" <<'PY'
import json, sys
download = json.loads(sys.argv[1])["downloads"]["server:default"]
print(download["name"], download["url"], download["checksums"]["sha256"])
PY
)

SERVER_JAR="$CACHE/$JAR_NAME"
sha_of() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
if [ ! -f "$SERVER_JAR" ] || [ "$(sha_of "$SERVER_JAR")" != "$JAR_SHA" ]; then
  echo "Downloading $JAR_NAME"
  curl -fsS --retry 4 --retry-delay 3 -o "$SERVER_JAR.part" "$JAR_URL"
  mv "$SERVER_JAR.part" "$SERVER_JAR"
  if [ "$(sha_of "$SERVER_JAR")" != "$JAR_SHA" ]; then
    echo "Checksum mismatch for $JAR_NAME" >&2
    rm -f "$SERVER_JAR"
    exit 1
  fi
fi

# A fresh server directory every time, so an earlier run cannot hide a problem.
RUN="$ROOT/integration/target/server-$PROJECT-$VERSION"
rm -rf "$RUN"
mkdir -p "$RUN/plugins"
cp "$PLUGIN" "$RUN/plugins/folia-integration.jar"
cp "$SERVER_JAR" "$RUN/server.jar"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<'PROPS'
online-mode=false
server-port=0
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=2
simulation-distance=2
max-players=2
motd=folia-libs integration test
enable-command-block=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
PROPS

echo "Starting $PROJECT $VERSION (timeout ${TIMEOUT_SECONDS}s)"
set +e
(
  cd "$RUN"
  exec java -Xmx1G -Dcom.mojang.eula.agree=true \
    -Dit.expect.folia="$EXPECT_FOLIA" -Dit.expect.version="$VERSION" \
    -jar server.jar --nogui < /dev/null > server.log 2>&1
) &
SERVER_PID=$!

waited=0
while kill -0 "$SERVER_PID" 2>/dev/null; do
  if [ "$waited" -ge "$TIMEOUT_SECONDS" ]; then
    echo "The server did not stop within ${TIMEOUT_SECONDS}s; killing it."
    kill "$SERVER_PID" 2>/dev/null
    sleep 5
    kill -9 "$SERVER_PID" 2>/dev/null
    break
  fi
  sleep 2
  waited=$((waited + 2))
done
wait "$SERVER_PID" 2>/dev/null
set -e

show_log_tail() { echo "---- last lines of $RUN/server.log ----"; tail -n 60 "$RUN/server.log"; }

if [ ! -f "$RUN/it-result.json" ]; then
  echo "FAILED: the plugin never wrote it-result.json, so the checks did not finish."
  show_log_tail
  exit 1
fi

STATUS=0
python3 - "$RUN/it-result.json" <<'PY' || STATUS=$?
import json, sys
result = json.load(open(sys.argv[1], encoding="utf-8"))
failed = False
for check in result["checks"]:
    status = "PASS" if check["ok"] else "FAIL"
    detail = check["detail"].strip().splitlines()
    print(f"  {status}  {check['name']}" + (f": {detail[0]}" if detail else ""))
    if not check["ok"]:
        failed = True
        for line in detail[1:]:
            print("        " + line)
sys.exit(1 if failed or not result["passed"] else 0)
PY

# Anything the libraries logged as an error is a failure even when every check passed.
if grep -E "\[FoliaIntegration\].*(SEVERE|ERROR)|^\s+at shaded\.(foliacommons|foliaboard|foliagui|folianpc)" "$RUN/server.log" \
    | grep -v "FAIL " > "$RUN/library-errors.txt"; then
  if [ -s "$RUN/library-errors.txt" ]; then
    echo "FAILED: the server log contains errors from the libraries:"
    head -n 30 "$RUN/library-errors.txt"
    exit 1
  fi
fi

if [ "$STATUS" -ne 0 ]; then
  show_log_tail
  exit 1
fi
echo "OK: $PROJECT $VERSION"
