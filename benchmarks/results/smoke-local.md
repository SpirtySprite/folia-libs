# Benchmark results

| | |
|---|---|
| JDK | 21.0.10 (OpenJDK 64-Bit Server VM) |
| JMH | 1.37 |
| Forks | 1 |
| Warmup | 0 x 1 s |
| Measurement | 1 x 200 ms |
| Machine | Linux x86_64, 4 logical CPUs |

Scores are the average time of one call (lower is better). The figure after the score is the 99.9% confidence interval across iterations. Numbers from different machines are not comparable; compare runs on the same machine.

## PlaceholdersBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| builtInPlaceholders |  | 1,752 ns/op | n/a |
| customCachedPlaceholder |  | 943.6 ns/op | n/a |
| customUncachedPlaceholder |  | 755.8 ns/op | n/a |
| noPlaceholders |  | 7.190 ns/op | n/a |
| resolveAndParseToComponent |  | 47,716 ns/op | n/a |

## SidebarDiffBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| changeAllLines | lines=5 | 346.6 ns/op | n/a |
| changeOneLine | lines=5 | 333.1 ns/op | n/a |
| setSameLine | lines=5 | 318.7 ns/op | n/a |
| setSameLines | lines=5 | 388.4 ns/op | n/a |

## TextBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| cachedHit |  | 2.031 ns/op | n/a |
| cachedMiss |  | 37,973 ns/op | n/a |
| miniGradient |  | 53,185 ns/op | n/a |
| miniSimple |  | 17,386 ns/op | n/a |
| parseLegacy |  | 25,605 ns/op | n/a |

## DiagnosticsBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| buildAndPrint | features=10 | 4.501 us/op | n/a |

## LegacyBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| stripColourCodes |  | 201.3 ns/op | n/a |
| toMiniColourCodes |  | 618.5 ns/op | n/a |
| toMiniHex |  | 316.1 ns/op | n/a |
| toMiniPlain |  | 8.492 ns/op | n/a |

## ServerVersionBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| isAtLeast |  | 0.606 ns/op | n/a |
| parse |  | 4,878 ns/op | n/a |

## GuiItemBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| namedAndLoreItem |  | 1,068,910 ns/op | n/a |
| namedAndLoreItemWithIdentity |  | 1,459,963 ns/op | n/a |
| plainItem |  | 10,532 ns/op | n/a |

## GuiTextBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| legacyTitle |  | 1,958 ns/op | n/a |
| miniGradient |  | 68,041 ns/op | n/a |
| templateWithValues |  | 2,755 ns/op | n/a |

## GuiUpdateBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| replaceAllSlotsAndUpdate |  | 1,089,842 ns/op | n/a |
| replaceOneSlotAndUpdate |  | 33,509 ns/op | n/a |
| updateNothingChanged |  | 240.6 ns/op | n/a |

## PaginatedGuiBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| jumpToNextPage | entries=100 | 1,212,533 ns/op | n/a |
| stepForwardOrWrap | entries=100 | 2,097,420 ns/op | n/a |

## NpcDataBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| buildWithBuilder |  | 135.7 ns/op | n/a |
| deserialize |  | 943.7 ns/op | n/a |
| serialize |  | 903.3 ns/op | n/a |

## NpcTickBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 15.0 us/op | n/a |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 21.1 us/op | n/a |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 13.2 us/op | n/a |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 24.7 us/op | n/a |

### Share of the time between visibility passes

The library runs one pass every two game ticks (100 ms). This is how much of that gap one pass uses, on one thread.

| Benchmark | Parameters | Pass time | Share of 100 ms |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 0.015 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 0.021 ms | 0.0% |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 0.013 ms | 0.0% |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 0.025 ms | 0.0% |

## PathfindingBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| findRoute | distance=16, obstacles=0 | 188.5 us/op | n/a |
| findRoute | distance=16, obstacles=20 | 99.3 us/op | n/a |
| giveUpOnUnreachableGoal | distance=16, obstacles=0 | 4,448 us/op | n/a |
| giveUpOnUnreachableGoal | distance=16, obstacles=20 | 5,308 us/op | n/a |

