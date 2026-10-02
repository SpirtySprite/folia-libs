#!/usr/bin/env bash
# Starts a real Paper or Folia server with the integration plugin installed and checks the result.
#
#   integration/scripts/run-server-test.sh <paper|folia> <minecraft-version> [plugin-jar]
#
# With BOT_JAR=<path to folia-integration-bot.jar> a headless client also joins the server, and the script checks
# the packets it received (sidebar, NPC, menu). Build it with `mvn -Pintegration package -DskipTests
# -pl integration-bot -am`; choose the client's protocol with -Dmcprotocollib.version=<version>.
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
  sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
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
if [ -n "${BOARD_FIXTURES_DIR:-}" ]; then
  cp "$BOARD_FIXTURES_DIR"/board-fixture-*.jar "$RUN/plugins/"
fi
cp "$SERVER_JAR" "$RUN/server.jar"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<'PROPS'
online-mode=false
server-port=__PORT__
enforce-secure-profile=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=2
simulation-distance=2
max-players=8
motd=folia-libs integration test
enable-command-block=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
PROPS

PORT="${IT_PORT:-25599}"
sed -i.bak "s/__PORT__/$PORT/" "$RUN/server.properties" && rm -f "$RUN/server.properties.bak"
BOT_JAR="${BOT_JAR:-}"
BOT_FLAG=false
DISPLAY_TWO_VIEWER="${DISPLAY_TWO_VIEWER:-false}"
if [ -n "$BOT_JAR" ]; then
  [ -f "$BOT_JAR" ] || { echo "Bot jar not found: $BOT_JAR" >&2; exit 2; }
  BOT_FLAG=true
fi

echo "Starting $PROJECT $VERSION (timeout ${TIMEOUT_SECONDS}s)"
set +e
(
  cd "$RUN"
  exec java -Xmx1G -Dcom.mojang.eula.agree=true \
    -Dit.expect.folia="$EXPECT_FOLIA" -Dit.expect.version="$VERSION" -Dit.bot="$BOT_FLAG" -Dit.display.two="$DISPLAY_TWO_VIEWER" \
    -jar server.jar --nogui < /dev/null > server.log 2>&1
) &
SERVER_PID=$!

if [ "$BOT_FLAG" = true ]; then
  # Let the server finish starting, then join as a player.
  started=0
  until grep -q "Done (" "$RUN/server.log" 2>/dev/null || ! kill -0 "$SERVER_PID" 2>/dev/null; do
    sleep 1
    started=$((started + 1))
    if [ "$started" -ge 240 ]; then break; fi
  done
  if kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "Joining with the test bot"
    if [ "$DISPLAY_TWO_VIEWER" = true ]; then
      (
        for attempt in $(seq 1 100); do
          grep -q DISPLAY_TWO_VIEWER_READY "$RUN/server.log" && break
          kill -0 "$SERVER_PID" 2>/dev/null || exit 1
          sleep 1
        done
        java -jar "$BOT_JAR" 127.0.0.1 "$PORT" "$RUN/observer-result.json" ItObserver 100 > "$RUN/observer.log" 2>&1
      ) &
      OBSERVER_PID=$!
    fi
    java -jar "$BOT_JAR" 127.0.0.1 "$PORT" "$RUN/bot-result.json" ItBot 100 > "$RUN/bot.log" 2>&1 || true
  fi
fi

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
if [ "$DISPLAY_TWO_VIEWER" = true ] && [ -n "${OBSERVER_PID:-}" ]; then wait "$OBSERVER_PID" 2>/dev/null; fi
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

# What the bot, an ordinary client, actually received.
if [ "$BOT_FLAG" = true ]; then
  if [ ! -f "$RUN/bot-result.json" ]; then
    echo "FAILED: the bot never wrote bot-result.json."
    cat "$RUN/bot.log" 2>/dev/null | tail -n 30
    exit 1
  fi
  python3 - "$RUN/bot-result.json" "${BOARD_FIXTURES_DIR:-}" <<'PY' || STATUS=$?
import json, sys
seen = json.load(open(sys.argv[1], encoding="utf-8"))
problems = []

def need(ok, what):
    print(f"  {'PASS' if ok else 'FAIL'}  client: {what}")
    if not ok:
        problems.append(what)

need(seen["joined"], "joined the server")
need(seen["disconnectedByServer"] and seen["disconnectReason"] == "integration done", "was sent away by the plugin at the end")
need(any(line.endswith("| IT Board") for line in seen["objectives"]), "received a sidebar titled 'IT Board'")
need(any(line.startswith("SIDEBAR") for line in seen["displays"]), "was told to display it in the sidebar slot")
scores = " ".join(seen["scores"])
need("Line one" in scores and "Line two" in scores, "received both sidebar lines")
need(any("| ItNpc |" in line and "ADD_PLAYER" in line for line in seen["playerInfo"]), "received the NPC's player list entry")
need(any(line.startswith("PLAYER") for line in seen["entities"]), "saw an NPC spawn as a player entity")
need(abs(seen["npcArrivalMovement"] - 0.25) < 1e-6, "received final NPC movement after arrival continuation")
need(seen["npcNametagMoved"], "received existing nametag display position update")
need(seen["npcEquipmentSeen"], "received NPC equipment")
need(seen["npcEquipmentCleared"], "received an explicit NPC equipment clear")
need(seen["boardFixedText"] and seen["boardItems"] > 0, "received managed text and item displays")
need(seen["boardFixedMoved"], "fixed display moved without recreation")
need(seen["boardPassengerMounted"], "received client passenger attachments")
need(seen["boardNativePassengerPreserved"], "client passenger attachments preserved a native passenger")
need(seen["boardSelfHidden"], "never received owner-hidden display text")
need(seen["boardPassengerSpawns"] >= 4, "passenger display was recreated after teleports")
need(seen["boardActiveDisplays"] == 0, "managed client displays were removed")
need(any("GENERIC_9X3" in line and "Integration Menu" in line for line in seen["screens"]), "was shown the 3 row menu 'Integration Menu'")
need(any("container=1" in line and "filled=1" in line for line in seen["containerContents"]), "received the menu's item")
if sys.argv[2]:
    for label in ("BoardFixtureA", "BoardFixtureB"):
        need(any(line.endswith("| " + label) for line in seen["objectives"]), "received " + label + " sidebar")
    active = seen["activeObjectives"]
    first_ids = {line.split(" | ")[0] for line in seen["objectives"] if line.endswith("| BoardFixtureA")}
    second_ids = {line.split(" | ")[0] for line in seen["objectives"] if line.endswith("| BoardFixtureB")}
    need(first_ids.isdisjoint(second_ids), "relocated copies used separate objective IDs")
    need(first_ids and not first_ids.intersection(active), "disabled owner removed its sidebar")
    need(second_ids and second_ids.intersection(active), "the other copy's sidebar remained registered")
    need("foreign-objective" in active and "foreign-team" in seen["activeTeams"], "foreign presentation identifiers remained registered")
    need(not any(value == "BoardFixtureA" for value in seen["activeTeams"].values()), "disabled owner removed its nametag")
    need(any(value == "BoardFixtureB" for value in seen["activeTeams"].values()), "the other copy's nametag remained registered")
    bars = seen["activeBossBars"].values()
    need("BoardFixtureA" not in bars and "BoardFixtureB" in bars, "disabled owner removed only its boss bar")
    need(any(" | ItBot | 11 |" in line for line in seen["scores"])
         and any(" | ItBot | 22 |" in line for line in seen["scores"]), "shared score updates preserved both viewer overrides")
    first_scores = {line.split(" | ")[0] for line in seen["scores"] if " | ItBot | 11 |" in line}
    second_scores = {line.split(" | ")[0] for line in seen["scores"] if " | ItBot | 22 |" in line}
    need(first_scores and not first_scores.intersection(active), "disabled owner removed its score objective")
    need(second_scores and second_scores.intersection(active), "the other copy's score objective remained registered")
    need(any(line.startswith("tab | BoardFixtureB |") for line in seen["presentationEvents"])
         and not any(line == "tab |  | " for line in seen["presentationEvents"]), "closing the first copy preserved the second copy's tab header")
sys.exit(1 if problems else 0)
PY
fi

if [ "$DISPLAY_TWO_VIEWER" = true ]; then
  python3 - "$RUN/bot-result.json" "$RUN/observer-result.json" <<'PY' || STATUS=$?
import json, sys
for path in sys.argv[1:]:
    with open(path, encoding="utf-8") as source:
        seen = json.load(source)
    assert seen["boardSelfHidden"], f"owner-hidden text appeared: {path}"
    assert seen["boardOtherOwnerSpawns"] > 0, f"remote nametag never appeared: {path}"
    assert seen["boardPassengerMounted"], f"remote nametag was not mounted: {path}"
    assert seen["boardActiveDisplays"] == 0, f"remote displays remained: {path}"
    print(f"  PASS  remote display client: {path}")
PY
fi

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
