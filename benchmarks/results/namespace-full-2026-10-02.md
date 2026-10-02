# Benchmark results

Measured source: [`ba38e63`](https://github.com/SpirtySprite/folia-libs/commit/ba38e63fda76054a81c1b021949da302bd4ad279). [Namespace workflow run](https://github.com/SpirtySprite/folia-libs/actions/runs/36990905310).

[Raw JMH JSON](namespace-full-2026-10-02.json) and [run provenance](namespace-full-2026-10-02.metadata.json). All 92 existing cases completed with GC profiling. This report was regenerated from the unchanged raw workflow output to include allocation columns.

The suite does not include dedicated managed-display or nametag-composition benchmarks. This full-mode Namespace run establishes a baseline; older quick-mode runs on other hardware do not establish before/after changes.

| | |
|---|---|
| JDK | 21.0.12.1 (OpenJDK 64-Bit Server VM) |
| JMH | 1.37 |
| Forks | 2 |
| Warmup | 3 x 1 s |
| Measurement | 5 x 1 s |
| Machine | Namespace profile namespace-profile-libs; Linux x86_64, 8 logical CPUs |

Scores are the average time of one call (lower is better). The figure after the score is the 99.9% confidence interval across iterations. Numbers from different machines are not comparable; compare runs on the same machine.

## PlaceholdersBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| builtInPlaceholders |  | 441.7 ns/op | ± 22.4 | 960 B |
| customCachedPlaceholder |  | 172.4 ns/op | ± 13.0 | 472 B |
| customUncachedPlaceholder |  | 136.8 ns/op | ± 8.074 | 456 B |
| noPlaceholders |  | 4.379 ns/op | ± 0.235 | 0 B |
| resolveAndParseToComponent |  | 3,471 ns/op | ± 88.4 | 7,816 B |

## SidebarDiffBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| changeAllLines | lines=5 | 122.5 ns/op | ± 5.180 | 374 B |
| changeAllLines | lines=15 | 293.7 ns/op | ± 11.8 | 1,035 B |
| changeOneLine | lines=5 | 94.5 ns/op | ± 5.403 | 278 B |
| changeOneLine | lines=15 | 187.0 ns/op | ± 11.5 | 699 B |
| setSameLine | lines=5 | 80.5 ns/op | ± 4.457 | 278 B |
| setSameLine | lines=15 | 177.9 ns/op | ± 14.3 | 699 B |
| setSameLines | lines=5 | 119.9 ns/op | ± 26.0 | 374 B |
| setSameLines | lines=15 | 294.1 ns/op | ± 56.7 | 1,035 B |

## TextBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| cachedHit |  | 4.813 ns/op | ± 0.540 | 0 B |
| cachedMiss |  | 3,927 ns/op | ± 1,043 | 7,480 B |
| miniGradient |  | 6,640 ns/op | ± 763.7 | 14,928 B |
| miniSimple |  | 3,545 ns/op | ± 515.4 | 7,008 B |
| parseLegacy |  | 4,564 ns/op | ± 989.3 | 9,280 B |

## DiagnosticsBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| buildAndPrint | features=10 | 0.481 us/op | ± 0.060 | 2,800 B |
| buildAndPrint | features=50 | 2.449 us/op | ± 0.189 | 10,948 B |

## LegacyBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| stripColourCodes |  | 85.2 ns/op | ± 10.0 | 128 B |
| toMiniColourCodes |  | 178.0 ns/op | ± 8.508 | 352 B |
| toMiniHex |  | 118.5 ns/op | ± 5.731 | 360 B |
| toMiniPlain |  | 6.211 ns/op | ± 0.108 | 0 B |

## ServerVersionBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| isAtLeast |  | 0.575 ns/op | ± 0.040 | 0 B |
| parse |  | 103.9 ns/op | ± 3.658 | 400 B |

## GuiItemBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| namedAndLoreItem |  | 12,935 ns/op | ± 809.9 | 77,833 B |
| namedAndLoreItemWithIdentity |  | 21,500 ns/op | ± 1,339 | 125,730 B |
| plainItem |  | 415.0 ns/op | ± 20.5 | 3,976 B |

## GuiTextBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| legacyTitle |  | 185.4 ns/op | ± 6.404 | 912 B |
| miniGradient |  | 4,517 ns/op | ± 181.5 | 12,834 B |
| templateWithValues |  | 354.2 ns/op | ± 19.8 | 1,584 B |

## GuiUpdateBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| replaceAllSlotsAndUpdate |  | 422,340 ns/op | ± 20,312 | 2,655,559 B |
| replaceOneSlotAndUpdate |  | 363,681 ns/op | ± 51,102 | 2,684,791 B |
| updateNothingChanged |  | 331,079 ns/op | ± 17,881 | 2,685,342 B |

## PaginatedGuiBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| jumpToNextPage | entries=100 | 354,493 ns/op | ± 29,554 | 2,337,568 B |
| jumpToNextPage | entries=1000 | 455,921 ns/op | ± 23,547 | 2,848,114 B |
| jumpToNextPage | entries=10000 | 452,176 ns/op | ± 13,335 | 2,909,153 B |
| stepForwardOrWrap | entries=100 | 367,415 ns/op | ± 16,694 | 2,337,451 B |
| stepForwardOrWrap | entries=1000 | 522,505 ns/op | ± 46,450 | 2,848,107 B |
| stepForwardOrWrap | entries=10000 | 482,665 ns/op | ± 26,251 | 2,915,284 B |

## NpcDataBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| buildWithBuilder |  | 42.6 ns/op | ± 2.548 | 344 B |
| deserialize |  | 1,651 ns/op | ± 83.0 | 6,560 B |
| serialize |  | 350.4 ns/op | ± 24.6 | 2,528 B |

## NpcTickBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 29.2 us/op | ± 2.097 | 39,144 B |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 30.3 us/op | ± 1.689 | 33,636 B |
| tickPlayersMoving | npcs=100, players=100, worldEdge=4000 | 18.8 us/op | ± 0.699 | 62,736 B |
| tickPlayersMoving | npcs=100, players=100, worldEdge=400 | 80.7 us/op | ± 4.059 | 108,340 B |
| tickPlayersMoving | npcs=100, players=500, worldEdge=4000 | 60.7 us/op | ± 1.805 | 127,646 B |
| tickPlayersMoving | npcs=100, players=500, worldEdge=400 | 344.6 us/op | ± 14.2 | 377,039 B |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=4000 | 279.1 us/op | ± 8.865 | 377,568 B |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=400 | 329.4 us/op | ± 32.2 | 322,880 B |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=4000 | 162.1 us/op | ± 5.566 | 490,359 B |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=400 | 867.7 us/op | ± 43.2 | 924,785 B |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=4000 | 389.6 us/op | ± 7.895 | 576,901 B |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=400 | 3,141 us/op | ± 117.4 | 3,073,297 B |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=4000 | 3,412 us/op | ± 633.6 | 3,761,703 B |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=400 | 4,520 us/op | ± 517.8 | 3,215,351 B |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=4000 | 2,085 us/op | ± 374.2 | 4,772,638 B |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=400 | 11,959 us/op | ± 1,664 | 8,971,181 B |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=4000 | 5,149 us/op | ± 838.5 | 5,163,606 B |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=400 | 44,059 us/op | ± 2,176 | 30,657,871 B |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 29.4 us/op | ± 0.663 | 39,096 B |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 28.7 us/op | ± 0.879 | 33,496 B |
| tickSteadyState | npcs=100, players=100, worldEdge=4000 | 14.2 us/op | ± 1.214 | 62,232 B |
| tickSteadyState | npcs=100, players=100, worldEdge=400 | 78.6 us/op | ± 10.5 | 101,497 B |
| tickSteadyState | npcs=100, players=500, worldEdge=4000 | 55.8 us/op | ± 12.8 | 121,600 B |
| tickSteadyState | npcs=100, players=500, worldEdge=400 | 348.0 us/op | ± 37.7 | 365,034 B |
| tickSteadyState | npcs=1000, players=10, worldEdge=4000 | 296.3 us/op | ± 12.9 | 377,498 B |
| tickSteadyState | npcs=1000, players=10, worldEdge=400 | 346.3 us/op | ± 27.8 | 321,498 B |
| tickSteadyState | npcs=1000, players=100, worldEdge=4000 | 149.6 us/op | ± 22.7 | 489,585 B |
| tickSteadyState | npcs=1000, players=100, worldEdge=400 | 747.2 us/op | ± 28.6 | 807,749 B |
| tickSteadyState | npcs=1000, players=500, worldEdge=4000 | 384.7 us/op | ± 25.3 | 575,891 B |
| tickSteadyState | npcs=1000, players=500, worldEdge=400 | 3,417 us/op | ± 410.2 | 2,914,696 B |
| tickSteadyState | npcs=10000, players=10, worldEdge=4000 | 4,956 us/op | ± 1,092 | 3,761,530 B |
| tickSteadyState | npcs=10000, players=10, worldEdge=400 | 5,859 us/op | ± 1,897 | 3,201,513 B |
| tickSteadyState | npcs=10000, players=100, worldEdge=4000 | 3,768 us/op | ± 966.7 | 4,782,478 B |
| tickSteadyState | npcs=10000, players=100, worldEdge=400 | 12,306 us/op | ± 2,314 | 8,061,037 B |
| tickSteadyState | npcs=10000, players=500, worldEdge=4000 | 6,839 us/op | ± 1,337 | 5,169,671 B |
| tickSteadyState | npcs=10000, players=500, worldEdge=400 | 48,059 us/op | ± 5,927 | 29,385,707 B |

### Share of the time between visibility passes

The library runs one pass every two game ticks (100 ms). This is how much of that gap one pass uses, on one thread.

| Benchmark | Parameters | Pass time | Share of 100 ms |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 0.029 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 0.030 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=100, worldEdge=4000 | 0.019 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=100, worldEdge=400 | 0.081 ms | 0.1% |
| tickPlayersMoving | npcs=100, players=500, worldEdge=4000 | 0.061 ms | 0.1% |
| tickPlayersMoving | npcs=100, players=500, worldEdge=400 | 0.345 ms | 0.3% |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=4000 | 0.279 ms | 0.3% |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=400 | 0.329 ms | 0.3% |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=4000 | 0.162 ms | 0.2% |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=400 | 0.868 ms | 0.9% |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=4000 | 0.390 ms | 0.4% |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=400 | 3.141 ms | 3.1% |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=4000 | 3.412 ms | 3.4% |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=400 | 4.520 ms | 4.5% |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=4000 | 2.085 ms | 2.1% |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=400 | 11.959 ms | 12.0% |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=4000 | 5.149 ms | 5.1% |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=400 | 44.059 ms | 44.1% |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 0.029 ms | 0.0% |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 0.029 ms | 0.0% |
| tickSteadyState | npcs=100, players=100, worldEdge=4000 | 0.014 ms | 0.0% |
| tickSteadyState | npcs=100, players=100, worldEdge=400 | 0.079 ms | 0.1% |
| tickSteadyState | npcs=100, players=500, worldEdge=4000 | 0.056 ms | 0.1% |
| tickSteadyState | npcs=100, players=500, worldEdge=400 | 0.348 ms | 0.3% |
| tickSteadyState | npcs=1000, players=10, worldEdge=4000 | 0.296 ms | 0.3% |
| tickSteadyState | npcs=1000, players=10, worldEdge=400 | 0.346 ms | 0.3% |
| tickSteadyState | npcs=1000, players=100, worldEdge=4000 | 0.150 ms | 0.1% |
| tickSteadyState | npcs=1000, players=100, worldEdge=400 | 0.747 ms | 0.7% |
| tickSteadyState | npcs=1000, players=500, worldEdge=4000 | 0.385 ms | 0.4% |
| tickSteadyState | npcs=1000, players=500, worldEdge=400 | 3.417 ms | 3.4% |
| tickSteadyState | npcs=10000, players=10, worldEdge=4000 | 4.956 ms | 5.0% |
| tickSteadyState | npcs=10000, players=10, worldEdge=400 | 5.859 ms | 5.9% |
| tickSteadyState | npcs=10000, players=100, worldEdge=4000 | 3.768 ms | 3.8% |
| tickSteadyState | npcs=10000, players=100, worldEdge=400 | 12.306 ms | 12.3% |
| tickSteadyState | npcs=10000, players=500, worldEdge=4000 | 6.839 ms | 6.8% |
| tickSteadyState | npcs=10000, players=500, worldEdge=400 | 48.059 ms | 48.1% |

## PathfindingBenchmark

| Benchmark | Parameters | Score | Error | Allocated/op |
|---|---|---:|---:|---:|
| findRoute | distance=16, obstacles=0 | 30.8 us/op | ± 2.240 | 84,128 B |
| findRoute | distance=16, obstacles=20 | 44.5 us/op | ± 12.3 | 99,184 B |
| findRoute | distance=32, obstacles=0 | 152.1 us/op | ± 14.6 | 316,105 B |
| findRoute | distance=32, obstacles=20 | 225.0 us/op | ± 25.7 | 381,026 B |
| findRoute | distance=64, obstacles=0 | 670.4 us/op | ± 129.2 | 1,197,821 B |
| findRoute | distance=64, obstacles=20 | 566.0 us/op | ± 40.3 | 961,452 B |
| giveUpOnUnreachableGoal | distance=16, obstacles=0 | 1,490 us/op | ± 241.5 | 3,509,290 B |
| giveUpOnUnreachableGoal | distance=16, obstacles=20 | 1,861 us/op | ± 107.6 | 3,291,781 B |
| giveUpOnUnreachableGoal | distance=32, obstacles=0 | 1,532 us/op | ± 373.9 | 3,509,291 B |
| giveUpOnUnreachableGoal | distance=32, obstacles=20 | 2,189 us/op | ± 209.1 | 3,355,831 B |
| giveUpOnUnreachableGoal | distance=64, obstacles=0 | 1,535 us/op | ± 89.0 | 3,509,291 B |
| giveUpOnUnreachableGoal | distance=64, obstacles=20 | 2,042 us/op | ± 142.0 | 3,419,478 B |
