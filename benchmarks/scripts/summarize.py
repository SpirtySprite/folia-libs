#!/usr/bin/env python3
"""Turns a JMH JSON result file into a readable markdown report.

usage: summarize.py results.json [machine description] > report.md

The machine description defaults to the computer running this script. Pass one when the results
came from somewhere else, for example "GitHub Actions ubuntu-latest runner, 4 vCPUs".
"""
import json
import os
import platform
import sys
from collections import OrderedDict

UNIT_ORDER = {"ns/op": 1, "us/op": 1000, "ms/op": 1000000, "s/op": 1000000000}


def nanos(score, unit):
    return score * UNIT_ORDER.get(unit, 1)


def fmt(score, unit):
    score = float(score)
    if score != score:
        return "n/a"
    if score >= 1000:
        return f"{score:,.0f}"
    if score >= 10:
        return f"{score:,.1f}"
    return f"{score:,.3f}"


def error_text(error, unit):
    text = fmt(error, unit)
    return text if text == "n/a" else f"± {text}"


def params_text(params):
    return ", ".join(f"{k}={v}" for k, v in params.items()) if params else ""


def main(path, machine=None):
    with open(path) as handle:
        results = json.load(handle)
    if not results:
        print("No results.")
        return

    first = results[0]
    print("# Benchmark results\n")
    print("| | |")
    print("|---|---|")
    print(f"| JDK | {first.get('jdkVersion', '?')} ({first.get('vmName', '?')}) |")
    print(f"| JMH | {first.get('jmhVersion', '?')} |")
    print(f"| Forks | {first.get('forks', '?')} |")
    print(f"| Warmup | {first.get('warmupIterations', '?')} x {first.get('warmupTime', '?')} |")
    print(f"| Measurement | {first.get('measurementIterations', '?')} x {first.get('measurementTime', '?')} |")
    machine = machine or f"{platform.system()} {platform.machine()}, {os.cpu_count()} logical CPUs"
    print(f"| Machine | {machine} |")
    print()
    print("Scores are the average time of one call (lower is better). The figure after the "
          "score is the 99.9% confidence interval across iterations. Numbers from different machines "
          "are not comparable; compare runs on the same machine.\n")

    groups = OrderedDict()
    for entry in sorted(results, key=lambda r: r["benchmark"]):
        cls, method = entry["benchmark"].rsplit(".", 1)
        groups.setdefault(cls.split(".")[-1], []).append((method, entry))

    for cls, rows in groups.items():
        print(f"## {cls}\n")
        has_alloc = any("secondaryMetrics" in e and "·gc.alloc.rate.norm" in e["secondaryMetrics"] for _, e in rows)
        header = "| Benchmark | Parameters | Score | Error |"
        rule = "|---|---|---:|---:|"
        if has_alloc:
            header += " Allocated/op |"
            rule += "---:|"
        print(header)
        print(rule)
        for method, entry in rows:
            metric = entry["primaryMetric"]
            unit = metric["scoreUnit"]
            line = (f"| {method} | {params_text(entry.get('params'))} | "
                    f"{fmt(metric['score'], unit)} {unit} | {error_text(metric['scoreError'], unit)} |")
            if has_alloc:
                alloc = entry.get("secondaryMetrics", {}).get("·gc.alloc.rate.norm")
                line += f" {alloc['score']:,.0f} B |" if alloc else " |"
            print(line)
        print()

        if cls == "NpcTickBenchmark":
            print("### Share of the time between visibility passes\n")
            print("The library runs one pass every two game ticks (100 ms). This is how much of that "
                  "gap one pass uses, on one thread.\n")
            print("| Benchmark | Parameters | Pass time | Share of 100 ms |")
            print("|---|---|---:|---:|")
            for method, entry in rows:
                metric = entry["primaryMetric"]
                ms = nanos(metric["score"], metric["scoreUnit"]) / 1_000_000
                print(f"| {method} | {params_text(entry.get('params'))} | {ms:,.3f} ms | {ms:.1f}% |")
            print()


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2] if len(sys.argv) == 3 else None)
