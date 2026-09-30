# Results

| File | What it is |
|---|---|
| `github-runner-quick.md` / `.json` | Quick-mode run on GitHub's shared `ubuntu-latest` runner (4 vCPUs). These are the figures quoted in the top-level README. |

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
