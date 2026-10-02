# Results

| File | What it is |
|---|---|
| `namespace-full-2026-10-02.md` / `.json` / `.metadata.json` | Full-mode run of the complete 92-case suite on Namespace profile `namespace-profile-libs`, after PRs #19, #20 and #21. Includes GC allocation metrics and source/run provenance. |
| `github-runner-quick.md` / `.json` | Quick-mode run on GitHub's shared `ubuntu-latest` runner (4 vCPUs). These are the figures quoted in the top-level README. |
| `github-runner-quick-npc-nearby-lookup.md` / `.json` | Quick-mode run of only the NPC visibility benchmark on the same kind of GitHub runner, after FoliaNPC started looking only at nearby players. These are the NPC figures quoted in the top-level README. The other benchmarks in `github-runner-quick` were not affected by that change. |
| `windows-16cpu-quick.md` | Quick-mode run on a Windows desktop PC (AMD64, 16 logical CPUs, JDK 25), made with the same commands. Used for the comparison in the top-level README. |

`github-runner-quick.json` combines two runs of the **Benchmarks** workflow, both on the same kind of runner:
the first run covered every benchmark; its pathfinding results were then replaced by a second run of only the
pathfinding benchmarks, because the first run's two 20%-obstacle cases at 16 and 32 blocks turned out to measure
a search that found no route (the goal tile was blocked). The benchmark was fixed to keep the goal reachable and to
fail its setup if a case has no route, and a separate benchmark now measures giving up on an unreachable goal.

**Quick mode** is 1 fork with 2 warmup and 3 measured iterations of half a second. It is enough to see sizes and
trends, but individual figures can be off by 10–30%, and some rows carry large error margins (shown in the
report). A full run (2 forks, 3 warmup and 5 measured iterations of one second) gives tighter numbers:

```bash
benchmarks/scripts/run.sh          # full run on your own machine
```

Numbers from different machines are not comparable. Add your own run here under a name that says where it was made.

The Namespace result measures commit `ba38e63fda76054a81c1b021949da302bd4ad279` using two forks,
three warmup iterations and five measured iterations per fork, each lasting one second.
The [workflow run](https://github.com/SpirtySprite/folia-libs/actions/runs/36990905310) also publishes
the original output as the `benchmark-results` artifact. This is a new runner baseline, not a
before/after comparison with the older quick-mode runs.
