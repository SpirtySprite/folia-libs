#!/usr/bin/env bash
# Runs the benchmarks and writes results/<label>.json plus results/<label>.md.
#
#   benchmarks/scripts/run.sh            full run, the settings written on each benchmark class
#   benchmarks/scripts/run.sh quick      one fork, shorter iterations; fine for a sanity check
#   benchmarks/scripts/run.sh smoke      one 200 ms iteration of every case; proves they all still run
#
# Environment:
#   LABEL     name for the output files (default: <date>-<hostname>)
#   FILTER    regular expression selecting benchmarks, e.g. FILTER=NpcTick
#   PROFILE   set to 1 to add JMH's GC profiler (reports bytes allocated per operation)
#   EXTRA     extra JMH arguments, e.g. EXTRA="-p npcs=1000"
set -euo pipefail

mode="${1:-full}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
jar="$root/benchmarks/target/benchmarks.jar"
label="${LABEL:-$(date +%Y%m%d)-$(hostname -s)}"
out="$root/benchmarks/results"
mkdir -p "$out"

if [ ! -f "$jar" ]; then
  echo "Building the benchmark jar..."
  (cd "$root" && mvn -B -ntp -q -Pbenchmarks package -DskipTests -pl benchmarks -am)
fi

case "$mode" in
  full)  args=() ;;
  quick) args=(-f 1 -wi 2 -i 3 -r 500ms -w 500ms) ;;
  smoke) args=(-f 1 -wi 0 -i 1 -r 200ms -foe true -p npcs=100 -p players=10 -p entries=100 -p lines=5 -p features=10 -p distance=16) ;;
  *) echo "unknown mode '$mode' (use full, quick or smoke)"; exit 2 ;;
esac

if [ "${PROFILE:-0}" = "1" ]; then
  args+=(-prof gc)
fi

# shellcheck disable=SC2086
java -jar "$jar" "${args[@]}" ${EXTRA:-} -rf json -rff "$out/$label.json" ${FILTER:+"$FILTER"}

python3 "$root/benchmarks/scripts/summarize.py" "$out/$label.json" > "$out/$label.md"
echo "Wrote $out/$label.json and $out/$label.md"
