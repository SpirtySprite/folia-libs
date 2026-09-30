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
| builtInPlaceholders |  | 2,501 ns/op | n/a |
| customCachedPlaceholder |  | 1,593 ns/op | n/a |
| customUncachedPlaceholder |  | 1,006 ns/op | n/a |
| noPlaceholders |  | 6.736 ns/op | n/a |
| resolveAndParseToComponent |  | 24,298 ns/op | n/a |

## SidebarDiffBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| changeAllLines | lines=5 | 237.6 ns/op | n/a |
| changeOneLine | lines=5 | 374.5 ns/op | n/a |
| setSameLine | lines=5 | 236.0 ns/op | n/a |
| setSameLines | lines=5 | 186.0 ns/op | n/a |

## TextBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| cachedHit |  | 2.159 ns/op | n/a |
| cachedMiss |  | 18,654 ns/op | n/a |
| miniGradient |  | 31,040 ns/op | n/a |
| miniSimple |  | 15,286 ns/op | n/a |
| parseLegacy |  | 20,087 ns/op | n/a |

## DiagnosticsBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| buildAndPrint | features=10 | 1.079 us/op | n/a |

## LegacyBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| stripColourCodes |  | 124.8 ns/op | n/a |
| toMiniColourCodes |  | 239.5 ns/op | n/a |
| toMiniHex |  | 166.5 ns/op | n/a |
| toMiniPlain |  | 8.578 ns/op | n/a |

## ServerVersionBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| isAtLeast |  | 0.574 ns/op | n/a |
| parse |  | 968.1 ns/op | n/a |

## GuiItemBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| namedAndLoreItem |  | 818,386 ns/op | n/a |
| namedAndLoreItemWithIdentity |  | 696,311 ns/op | n/a |
| plainItem |  | 7,162 ns/op | n/a |

## GuiTextBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| legacyTitle |  | 1,316 ns/op | n/a |
| miniGradient |  | 81,599 ns/op | n/a |
| templateWithValues |  | 2,866 ns/op | n/a |

## GuiUpdateBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| replaceAllSlotsAndUpdate |  | 1,187,300 ns/op | n/a |
| replaceOneSlotAndUpdate |  | 30,053 ns/op | n/a |
| updateNothingChanged |  | 244.2 ns/op | n/a |

## PaginatedGuiBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| jumpToNextPage | entries=100 | 2,215,598 ns/op | n/a |
| stepForwardOrWrap | entries=100 | 2,588,983 ns/op | n/a |

## NpcDataBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| buildWithBuilder |  | 438.6 ns/op | n/a |
| deserialize |  | 2,078 ns/op | n/a |
| serialize |  | 1,075 ns/op | n/a |

## NpcTickBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 16.2 us/op | n/a |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 23.6 us/op | n/a |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 16.1 us/op | n/a |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 42.4 us/op | n/a |

### Share of the time between visibility passes

The library runs one pass every two game ticks (100 ms). This is how much of that gap one pass uses, on one thread.

| Benchmark | Parameters | Pass time | Share of 100 ms |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 0.016 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 0.024 ms | 0.0% |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 0.016 ms | 0.0% |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 0.042 ms | 0.0% |

## PathfindingBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| findRoute | distance=16, obstacles=0 | 166.8 us/op | n/a |
| findRoute | distance=16, obstacles=20 | 199.1 us/op | n/a |
| giveUpOnUnreachableGoal | distance=16, obstacles=0 | 4,304 us/op | n/a |
| giveUpOnUnreachableGoal | distance=16, obstacles=20 | 5,463 us/op | n/a |

