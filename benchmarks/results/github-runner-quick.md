# Benchmark results

| | |
|---|---|
| JDK | 21.0.12.1 (OpenJDK 64-Bit Server VM) |
| JMH | 1.37 |
| Forks | 1 |
| Warmup | 2 x 500 ms |
| Measurement | 3 x 500 ms |
| Machine | GitHub Actions ubuntu-latest runner (shared), 4 vCPUs |

Scores are the average time of one call (lower is better). The figure after the score is the 99.9% confidence interval across iterations. Numbers from different machines are not comparable; compare runs on the same machine.

## PlaceholdersBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| builtInPlaceholders |  | 713.7 ns/op | ± 38.5 |
| customCachedPlaceholder |  | 248.1 ns/op | ± 16.4 |
| customUncachedPlaceholder |  | 230.2 ns/op | ± 5.016 |
| noPlaceholders |  | 5.960 ns/op | ± 0.265 |
| resolveAndParseToComponent |  | 5,368 ns/op | ± 714.0 |

## SidebarDiffBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| changeAllLines | lines=5 | 181.8 ns/op | ± 15.2 |
| changeAllLines | lines=15 | 443.5 ns/op | ± 24.6 |
| changeOneLine | lines=5 | 143.3 ns/op | ± 75.7 |
| changeOneLine | lines=15 | 270.1 ns/op | ± 19.3 |
| setSameLine | lines=5 | 117.7 ns/op | ± 2.044 |
| setSameLine | lines=15 | 261.7 ns/op | ± 1.221 |
| setSameLines | lines=5 | 152.8 ns/op | ± 22.4 |
| setSameLines | lines=15 | 343.6 ns/op | ± 71.4 |

## TextBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| cachedHit |  | 2.256 ns/op | ± 0.083 |
| cachedMiss |  | 4,823 ns/op | ± 445.4 |
| miniGradient |  | 8,594 ns/op | ± 1,593 |
| miniSimple |  | 4,857 ns/op | ± 249.0 |
| parseLegacy |  | 6,269 ns/op | ± 194.6 |

## DiagnosticsBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| buildAndPrint | features=10 | 0.521 us/op | ± 0.024 |
| buildAndPrint | features=50 | 2.235 us/op | ± 0.178 |

## LegacyBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| stripColourCodes |  | 115.8 ns/op | ± 2.810 |
| toMiniColourCodes |  | 263.4 ns/op | ± 7.663 |
| toMiniHex |  | 186.8 ns/op | ± 4.179 |
| toMiniPlain |  | 9.639 ns/op | ± 0.423 |

## ServerVersionBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| isAtLeast |  | 0.831 ns/op | ± 0.045 |
| parse |  | 538.7 ns/op | ± 59.6 |

## GuiItemBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| namedAndLoreItem |  | 22,020 ns/op | ± 131,467 |
| namedAndLoreItemWithIdentity |  | 32,979 ns/op | ± 140,582 |
| plainItem |  | 632.2 ns/op | ± 38.1 |

## GuiTextBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| legacyTitle |  | 374.7 ns/op | ± 13.4 |
| miniGradient |  | 8,019 ns/op | ± 12,278 |
| templateWithValues |  | 641.1 ns/op | ± 17.5 |

## GuiUpdateBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| replaceAllSlotsAndUpdate |  | 365,726 ns/op | ± 36,748 |
| replaceOneSlotAndUpdate |  | 7,197 ns/op | ± 1,023 |
| updateNothingChanged |  | 195.8 ns/op | ± 9.099 |

## PaginatedGuiBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| jumpToNextPage | entries=100 | 345,019 ns/op | ± 495,644 |
| jumpToNextPage | entries=1000 | 395,191 ns/op | ± 110,079 |
| jumpToNextPage | entries=10000 | 435,266 ns/op | ± 883,872 |
| stepForwardOrWrap | entries=100 | 323,859 ns/op | ± 293,486 |
| stepForwardOrWrap | entries=1000 | 416,739 ns/op | ± 591,938 |
| stepForwardOrWrap | entries=10000 | 429,010 ns/op | ± 398,544 |

## NpcDataBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| buildWithBuilder |  | 61.6 ns/op | ± 1.981 |
| deserialize |  | 806.4 ns/op | ± 15.1 |
| serialize |  | 551.0 ns/op | ± 14.1 |

## NpcTickBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 13.6 us/op | ± 5.063 |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 18.6 us/op | ± 1.595 |
| tickPlayersMoving | npcs=100, players=100, worldEdge=4000 | 103.8 us/op | ± 4.124 |
| tickPlayersMoving | npcs=100, players=100, worldEdge=400 | 130.0 us/op | ± 14.4 |
| tickPlayersMoving | npcs=100, players=500, worldEdge=4000 | 522.1 us/op | ± 17.9 |
| tickPlayersMoving | npcs=100, players=500, worldEdge=400 | 718.0 us/op | ± 94.4 |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=4000 | 133.7 us/op | ± 0.425 |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=400 | 208.8 us/op | ± 26.5 |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=4000 | 1,008 us/op | ± 107.2 |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=400 | 1,360 us/op | ± 334.8 |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=4000 | 5,355 us/op | ± 209.7 |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=400 | 7,047 us/op | ± 316.7 |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=4000 | 1,290 us/op | ± 14.5 |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=400 | 1,970 us/op | ± 505.6 |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=4000 | 9,837 us/op | ± 194.9 |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=400 | 13,249 us/op | ± 2,240 |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=4000 | 53,733 us/op | ± 1,799 |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=400 | 68,344 us/op | ± 10,661 |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 12.5 us/op | ± 1.135 |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 14.9 us/op | ± 1.214 |
| tickSteadyState | npcs=100, players=100, worldEdge=4000 | 94.4 us/op | ± 20.7 |
| tickSteadyState | npcs=100, players=100, worldEdge=400 | 133.6 us/op | ± 134.1 |
| tickSteadyState | npcs=100, players=500, worldEdge=4000 | 493.1 us/op | ± 49.4 |
| tickSteadyState | npcs=100, players=500, worldEdge=400 | 626.1 us/op | ± 31.3 |
| tickSteadyState | npcs=1000, players=10, worldEdge=4000 | 129.8 us/op | ± 2.086 |
| tickSteadyState | npcs=1000, players=10, worldEdge=400 | 158.9 us/op | ± 52.2 |
| tickSteadyState | npcs=1000, players=100, worldEdge=4000 | 945.1 us/op | ± 44.4 |
| tickSteadyState | npcs=1000, players=100, worldEdge=400 | 1,250 us/op | ± 295.3 |
| tickSteadyState | npcs=1000, players=500, worldEdge=4000 | 5,074 us/op | ± 954.0 |
| tickSteadyState | npcs=1000, players=500, worldEdge=400 | 6,471 us/op | ± 629.7 |
| tickSteadyState | npcs=10000, players=10, worldEdge=4000 | 1,316 us/op | ± 6.691 |
| tickSteadyState | npcs=10000, players=10, worldEdge=400 | 1,563 us/op | ± 279.3 |
| tickSteadyState | npcs=10000, players=100, worldEdge=4000 | 9,895 us/op | ± 1,905 |
| tickSteadyState | npcs=10000, players=100, worldEdge=400 | 12,929 us/op | ± 758.6 |
| tickSteadyState | npcs=10000, players=500, worldEdge=4000 | 47,829 us/op | ± 3,693 |
| tickSteadyState | npcs=10000, players=500, worldEdge=400 | 64,340 us/op | ± 9,078 |

### Share of the time between visibility passes

The library runs one pass every two game ticks (100 ms). This is how much of that gap one pass uses, on one thread.

| Benchmark | Parameters | Pass time | Share of 100 ms |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 0.014 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 0.019 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=100, worldEdge=4000 | 0.104 ms | 0.1% |
| tickPlayersMoving | npcs=100, players=100, worldEdge=400 | 0.130 ms | 0.1% |
| tickPlayersMoving | npcs=100, players=500, worldEdge=4000 | 0.522 ms | 0.5% |
| tickPlayersMoving | npcs=100, players=500, worldEdge=400 | 0.718 ms | 0.7% |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=4000 | 0.134 ms | 0.1% |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=400 | 0.209 ms | 0.2% |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=4000 | 1.008 ms | 1.0% |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=400 | 1.360 ms | 1.4% |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=4000 | 5.355 ms | 5.4% |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=400 | 7.047 ms | 7.0% |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=4000 | 1.290 ms | 1.3% |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=400 | 1.970 ms | 2.0% |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=4000 | 9.837 ms | 9.8% |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=400 | 13.249 ms | 13.2% |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=4000 | 53.733 ms | 53.7% |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=400 | 68.344 ms | 68.3% |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 0.012 ms | 0.0% |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 0.015 ms | 0.0% |
| tickSteadyState | npcs=100, players=100, worldEdge=4000 | 0.094 ms | 0.1% |
| tickSteadyState | npcs=100, players=100, worldEdge=400 | 0.134 ms | 0.1% |
| tickSteadyState | npcs=100, players=500, worldEdge=4000 | 0.493 ms | 0.5% |
| tickSteadyState | npcs=100, players=500, worldEdge=400 | 0.626 ms | 0.6% |
| tickSteadyState | npcs=1000, players=10, worldEdge=4000 | 0.130 ms | 0.1% |
| tickSteadyState | npcs=1000, players=10, worldEdge=400 | 0.159 ms | 0.2% |
| tickSteadyState | npcs=1000, players=100, worldEdge=4000 | 0.945 ms | 0.9% |
| tickSteadyState | npcs=1000, players=100, worldEdge=400 | 1.250 ms | 1.2% |
| tickSteadyState | npcs=1000, players=500, worldEdge=4000 | 5.074 ms | 5.1% |
| tickSteadyState | npcs=1000, players=500, worldEdge=400 | 6.471 ms | 6.5% |
| tickSteadyState | npcs=10000, players=10, worldEdge=4000 | 1.316 ms | 1.3% |
| tickSteadyState | npcs=10000, players=10, worldEdge=400 | 1.563 ms | 1.6% |
| tickSteadyState | npcs=10000, players=100, worldEdge=4000 | 9.895 ms | 9.9% |
| tickSteadyState | npcs=10000, players=100, worldEdge=400 | 12.929 ms | 12.9% |
| tickSteadyState | npcs=10000, players=500, worldEdge=4000 | 47.829 ms | 47.8% |
| tickSteadyState | npcs=10000, players=500, worldEdge=400 | 64.340 ms | 64.3% |

## PathfindingBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| findRoute | distance=16, obstacles=0 | 42.4 us/op | ± 2.171 |
| findRoute | distance=16, obstacles=20 | 43.0 us/op | ± 2.087 |
| findRoute | distance=32, obstacles=0 | 192.0 us/op | ± 41.5 |
| findRoute | distance=32, obstacles=20 | 238.2 us/op | ± 17.6 |
| findRoute | distance=64, obstacles=0 | 1,192 us/op | ± 35.2 |
| findRoute | distance=64, obstacles=20 | 870.4 us/op | ± 100.1 |
| giveUpOnUnreachableGoal | distance=16, obstacles=0 | 2,399 us/op | ± 619.0 |
| giveUpOnUnreachableGoal | distance=16, obstacles=20 | 3,004 us/op | ± 66.0 |
| giveUpOnUnreachableGoal | distance=32, obstacles=0 | 2,333 us/op | ± 470.5 |
| giveUpOnUnreachableGoal | distance=32, obstacles=20 | 2,998 us/op | ± 27.4 |
| giveUpOnUnreachableGoal | distance=64, obstacles=0 | 2,324 us/op | ± 319.8 |
| giveUpOnUnreachableGoal | distance=64, obstacles=20 | 3,082 us/op | ± 153.4 |

