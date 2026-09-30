# Benchmark results

| | |
|---|---|
| JDK | 25.0.3 (OpenJDK 64-Bit Server VM) |
| JMH | 1.37 |
| Forks | 1 |
| Warmup | 2 x 500 ms |
| Measurement | 3 x 500 ms |
| Machine | Windows AMD64, 16 logical CPUs |

Scores are the average time of one call (lower is better). The figure after the score is the 99.9% confidence interval across iterations. Numbers from different machines are not comparable; compare runs on the same machine.

## PlaceholdersBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| builtInPlaceholders |  | 506.1 ns/op | ▒ 32.3 |
| customCachedPlaceholder |  | 171.9 ns/op | ▒ 34.4 |
| customUncachedPlaceholder |  | 178.8 ns/op | ▒ 21.7 |
| noPlaceholders |  | 4.178 ns/op | ▒ 0.459 |
| resolveAndParseToComponent |  | 3,833 ns/op | ▒ 1,020 |

## SidebarDiffBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| changeAllLines | lines=5 | 146.3 ns/op | ▒ 7.015 |
| changeAllLines | lines=15 | 390.1 ns/op | ▒ 44.7 |
| changeOneLine | lines=5 | 108.1 ns/op | ▒ 20.9 |
| changeOneLine | lines=15 | 273.9 ns/op | ▒ 14.4 |
| setSameLine | lines=5 | 105.9 ns/op | ▒ 67.1 |
| setSameLine | lines=15 | 253.0 ns/op | ▒ 72.2 |
| setSameLines | lines=5 | 120.0 ns/op | ▒ 6.041 |
| setSameLines | lines=15 | 309.3 ns/op | ▒ 16.8 |

## TextBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| cachedHit |  | 1.275 ns/op | ▒ 0.038 |
| cachedMiss |  | 3,438 ns/op | ▒ 270.0 |
| miniGradient |  | 5,794 ns/op | ▒ 1,562 |
| miniSimple |  | 3,286 ns/op | ▒ 54.2 |
| parseLegacy |  | 4,374 ns/op | ▒ 462.9 |

## DiagnosticsBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| buildAndPrint | features=10 | 0.395 us/op | ▒ 0.168 |
| buildAndPrint | features=50 | 1.770 us/op | ▒ 0.078 |

## LegacyBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| stripColourCodes |  | 107.6 ns/op | ▒ 10.4 |
| toMiniColourCodes |  | 141.3 ns/op | ▒ 13.5 |
| toMiniHex |  | 150.6 ns/op | ▒ 179.6 |
| toMiniPlain |  | 6.591 ns/op | ▒ 0.347 |

## ServerVersionBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| isAtLeast |  | 0.580 ns/op | ▒ 0.169 |
| parse |  | 357.6 ns/op | ▒ 74.1 |

## GuiItemBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| namedAndLoreItem |  | 13,890 ns/op | ▒ 14,348 |
| namedAndLoreItemWithIdentity |  | 21,510 ns/op | ▒ 9,710 |
| plainItem |  | 556.9 ns/op | ▒ 1,344 |

## GuiTextBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| legacyTitle |  | 212.7 ns/op | ▒ 15.3 |
| miniGradient |  | 4,980 ns/op | ▒ 798.2 |
| templateWithValues |  | 437.6 ns/op | ▒ 65.9 |

## GuiUpdateBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| replaceAllSlotsAndUpdate |  | 283,243 ns/op | ▒ 107,160 |
| replaceOneSlotAndUpdate |  | 5,563 ns/op | ▒ 1,777 |
| updateNothingChanged |  | 168.8 ns/op | ▒ 30.4 |

## PaginatedGuiBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| jumpToNextPage | entries=100 | 254,071 ns/op | ▒ 168,387 |
| jumpToNextPage | entries=1000 | 334,052 ns/op | ▒ 451,232 |
| jumpToNextPage | entries=10000 | 326,773 ns/op | ▒ 36,485 |
| stepForwardOrWrap | entries=100 | 238,134 ns/op | ▒ 50,744 |
| stepForwardOrWrap | entries=1000 | 301,988 ns/op | ▒ 68,395 |
| stepForwardOrWrap | entries=10000 | 311,322 ns/op | ▒ 44,621 |

## NpcDataBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| buildWithBuilder |  | 40.5 ns/op | ▒ 4.281 |
| deserialize |  | 571.5 ns/op | ▒ 37.6 |
| serialize |  | 353.5 ns/op | ▒ 63.7 |

## NpcTickBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 10.1 us/op | ▒ 4.410 |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 13.1 us/op | ▒ 2.779 |
| tickPlayersMoving | npcs=100, players=100, worldEdge=4000 | 76.6 us/op | ▒ 14.4 |
| tickPlayersMoving | npcs=100, players=100, worldEdge=400 | 93.0 us/op | ▒ 15.7 |
| tickPlayersMoving | npcs=100, players=500, worldEdge=4000 | 398.6 us/op | ▒ 31.1 |
| tickPlayersMoving | npcs=100, players=500, worldEdge=400 | 512.0 us/op | ▒ 112.1 |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=4000 | 99.1 us/op | ▒ 17.7 |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=400 | 138.7 us/op | ▒ 10.5 |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=4000 | 744.4 us/op | ▒ 177.8 |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=400 | 1,016 us/op | ▒ 690.9 |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=4000 | 3,834 us/op | ▒ 1,585 |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=400 | 4,918 us/op | ▒ 869.4 |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=4000 | 1,006 us/op | ▒ 138.8 |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=400 | 1,482 us/op | ▒ 1,176 |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=4000 | 7,538 us/op | ▒ 1,166 |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=400 | 10,101 us/op | ▒ 3,146 |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=4000 | 39,847 us/op | ▒ 2,882 |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=400 | 52,329 us/op | ▒ 10,841 |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 8.824 us/op | ▒ 2.479 |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 10.5 us/op | ▒ 1.179 |
| tickSteadyState | npcs=100, players=100, worldEdge=4000 | 67.7 us/op | ▒ 11.6 |
| tickSteadyState | npcs=100, players=100, worldEdge=400 | 90.5 us/op | ▒ 32.5 |
| tickSteadyState | npcs=100, players=500, worldEdge=4000 | 398.7 us/op | ▒ 418.7 |
| tickSteadyState | npcs=100, players=500, worldEdge=400 | 455.7 us/op | ▒ 91.7 |
| tickSteadyState | npcs=1000, players=10, worldEdge=4000 | 98.1 us/op | ▒ 10.4 |
| tickSteadyState | npcs=1000, players=10, worldEdge=400 | 115.2 us/op | ▒ 26.3 |
| tickSteadyState | npcs=1000, players=100, worldEdge=4000 | 742.8 us/op | ▒ 160.6 |
| tickSteadyState | npcs=1000, players=100, worldEdge=400 | 975.5 us/op | ▒ 60.9 |
| tickSteadyState | npcs=1000, players=500, worldEdge=4000 | 3,612 us/op | ▒ 1,881 |
| tickSteadyState | npcs=1000, players=500, worldEdge=400 | 4,673 us/op | ▒ 170.7 |
| tickSteadyState | npcs=10000, players=10, worldEdge=4000 | 1,043 us/op | ▒ 965.6 |
| tickSteadyState | npcs=10000, players=10, worldEdge=400 | 1,159 us/op | ▒ 272.4 |
| tickSteadyState | npcs=10000, players=100, worldEdge=4000 | 7,698 us/op | ▒ 3,454 |
| tickSteadyState | npcs=10000, players=100, worldEdge=400 | 9,930 us/op | ▒ 5,555 |
| tickSteadyState | npcs=10000, players=500, worldEdge=4000 | 40,870 us/op | ▒ 36,757 |
| tickSteadyState | npcs=10000, players=500, worldEdge=400 | 50,142 us/op | ▒ 11,188 |

### Share of the time between visibility passes

The library runs one pass every two game ticks (100 ms). This is how much of that gap one pass uses, on one thread.

| Benchmark | Parameters | Pass time | Share of 100 ms |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 0.010 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 0.013 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=100, worldEdge=4000 | 0.077 ms | 0.1% |
| tickPlayersMoving | npcs=100, players=100, worldEdge=400 | 0.093 ms | 0.1% |
| tickPlayersMoving | npcs=100, players=500, worldEdge=4000 | 0.399 ms | 0.4% |
| tickPlayersMoving | npcs=100, players=500, worldEdge=400 | 0.512 ms | 0.5% |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=4000 | 0.099 ms | 0.1% |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=400 | 0.139 ms | 0.1% |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=4000 | 0.744 ms | 0.7% |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=400 | 1.016 ms | 1.0% |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=4000 | 3.834 ms | 3.8% |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=400 | 4.918 ms | 4.9% |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=4000 | 1.006 ms | 1.0% |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=400 | 1.482 ms | 1.5% |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=4000 | 7.538 ms | 7.5% |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=400 | 10.101 ms | 10.1% |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=4000 | 39.847 ms | 39.8% |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=400 | 52.329 ms | 52.3% |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 0.009 ms | 0.0% |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 0.011 ms | 0.0% |
| tickSteadyState | npcs=100, players=100, worldEdge=4000 | 0.068 ms | 0.1% |
| tickSteadyState | npcs=100, players=100, worldEdge=400 | 0.090 ms | 0.1% |
| tickSteadyState | npcs=100, players=500, worldEdge=4000 | 0.399 ms | 0.4% |
| tickSteadyState | npcs=100, players=500, worldEdge=400 | 0.456 ms | 0.5% |
| tickSteadyState | npcs=1000, players=10, worldEdge=4000 | 0.098 ms | 0.1% |
| tickSteadyState | npcs=1000, players=10, worldEdge=400 | 0.115 ms | 0.1% |
| tickSteadyState | npcs=1000, players=100, worldEdge=4000 | 0.743 ms | 0.7% |
| tickSteadyState | npcs=1000, players=100, worldEdge=400 | 0.976 ms | 1.0% |
| tickSteadyState | npcs=1000, players=500, worldEdge=4000 | 3.612 ms | 3.6% |
| tickSteadyState | npcs=1000, players=500, worldEdge=400 | 4.673 ms | 4.7% |
| tickSteadyState | npcs=10000, players=10, worldEdge=4000 | 1.043 ms | 1.0% |
| tickSteadyState | npcs=10000, players=10, worldEdge=400 | 1.159 ms | 1.2% |
| tickSteadyState | npcs=10000, players=100, worldEdge=4000 | 7.698 ms | 7.7% |
| tickSteadyState | npcs=10000, players=100, worldEdge=400 | 9.930 ms | 9.9% |
| tickSteadyState | npcs=10000, players=500, worldEdge=4000 | 40.870 ms | 40.9% |
| tickSteadyState | npcs=10000, players=500, worldEdge=400 | 50.142 ms | 50.1% |

## PathfindingBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| findRoute | distance=16, obstacles=0 | 44.1 us/op | ▒ 2.563 |
| findRoute | distance=16, obstacles=20 | 46.3 us/op | ▒ 3.595 |
| findRoute | distance=32, obstacles=0 | 241.0 us/op | ▒ 29.1 |
| findRoute | distance=32, obstacles=20 | 293.9 us/op | ▒ 77.2 |
| findRoute | distance=64, obstacles=0 | 988.5 us/op | ▒ 195.0 |
| findRoute | distance=64, obstacles=20 | 750.9 us/op | ▒ 371.3 |
| giveUpOnUnreachableGoal | distance=16, obstacles=0 | 2,326 us/op | ▒ 861.0 |
| giveUpOnUnreachableGoal | distance=16, obstacles=20 | 2,876 us/op | ▒ 324.2 |
| giveUpOnUnreachableGoal | distance=32, obstacles=0 | 2,199 us/op | ▒ 1,862 |
| giveUpOnUnreachableGoal | distance=32, obstacles=20 | 2,695 us/op | ▒ 343.0 |
| giveUpOnUnreachableGoal | distance=64, obstacles=0 | 2,108 us/op | ▒ 796.2 |
| giveUpOnUnreachableGoal | distance=64, obstacles=20 | 2,699 us/op | ▒ 293.6 |

