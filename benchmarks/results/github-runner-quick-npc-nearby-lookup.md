# Benchmark results

| | |
|---|---|
| JDK | 21.0.12.1 (OpenJDK 64-Bit Server VM) |
| JMH | 1.37 |
| Forks | 1 |
| Warmup | 2 x 500 ms |
| Measurement | 3 x 500 ms |
| Machine | Linux x86_64, 4 logical CPUs |

Scores are the average time of one call (lower is better). The figure after the score is the 99.9% confidence interval across iterations. Numbers from different machines are not comparable; compare runs on the same machine.

## NpcTickBenchmark

| Benchmark | Parameters | Score | Error |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 9.327 us/op | ± 3.898 |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 11.2 us/op | ± 0.672 |
| tickPlayersMoving | npcs=100, players=100, worldEdge=4000 | 16.1 us/op | ± 17.8 |
| tickPlayersMoving | npcs=100, players=100, worldEdge=400 | 54.9 us/op | ± 3.079 |
| tickPlayersMoving | npcs=100, players=500, worldEdge=4000 | 55.4 us/op | ± 4.414 |
| tickPlayersMoving | npcs=100, players=500, worldEdge=400 | 221.1 us/op | ± 14.6 |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=4000 | 82.5 us/op | ± 14.7 |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=400 | 119.7 us/op | ± 43.7 |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=4000 | 151.5 us/op | ± 52.9 |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=400 | 631.6 us/op | ± 201.2 |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=4000 | 372.7 us/op | ± 83.9 |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=400 | 1,850 us/op | ± 147.7 |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=4000 | 799.3 us/op | ± 44.3 |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=400 | 1,165 us/op | ± 287.4 |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=4000 | 1,755 us/op | ± 313.4 |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=400 | 6,697 us/op | ± 14,229 |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=4000 | 3,446 us/op | ± 760.3 |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=400 | 27,940 us/op | ± 7,435 |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 7.923 us/op | ± 1.038 |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 9.238 us/op | ± 0.458 |
| tickSteadyState | npcs=100, players=100, worldEdge=4000 | 10.3 us/op | ± 0.401 |
| tickSteadyState | npcs=100, players=100, worldEdge=400 | 42.1 us/op | ± 3.589 |
| tickSteadyState | npcs=100, players=500, worldEdge=4000 | 30.4 us/op | ± 0.612 |
| tickSteadyState | npcs=100, players=500, worldEdge=400 | 176.6 us/op | ± 75.7 |
| tickSteadyState | npcs=1000, players=10, worldEdge=4000 | 80.0 us/op | ± 7.353 |
| tickSteadyState | npcs=1000, players=10, worldEdge=400 | 94.4 us/op | ± 6.712 |
| tickSteadyState | npcs=1000, players=100, worldEdge=4000 | 74.0 us/op | ± 6.256 |
| tickSteadyState | npcs=1000, players=100, worldEdge=400 | 394.2 us/op | ± 87.9 |
| tickSteadyState | npcs=1000, players=500, worldEdge=4000 | 215.6 us/op | ± 117.5 |
| tickSteadyState | npcs=1000, players=500, worldEdge=400 | 1,607 us/op | ± 717.0 |
| tickSteadyState | npcs=10000, players=10, worldEdge=4000 | 932.3 us/op | ± 790.3 |
| tickSteadyState | npcs=10000, players=10, worldEdge=400 | 968.6 us/op | ± 93.7 |
| tickSteadyState | npcs=10000, players=100, worldEdge=4000 | 1,657 us/op | ± 363.1 |
| tickSteadyState | npcs=10000, players=100, worldEdge=400 | 4,742 us/op | ± 5,735 |
| tickSteadyState | npcs=10000, players=500, worldEdge=4000 | 3,095 us/op | ± 1,667 |
| tickSteadyState | npcs=10000, players=500, worldEdge=400 | 19,881 us/op | ± 31,865 |

### Share of the time between visibility passes

The library runs one pass every two game ticks (100 ms). This is how much of that gap one pass uses, on one thread.

| Benchmark | Parameters | Pass time | Share of 100 ms |
|---|---|---:|---:|
| tickPlayersMoving | npcs=100, players=10, worldEdge=4000 | 0.009 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=10, worldEdge=400 | 0.011 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=100, worldEdge=4000 | 0.016 ms | 0.0% |
| tickPlayersMoving | npcs=100, players=100, worldEdge=400 | 0.055 ms | 0.1% |
| tickPlayersMoving | npcs=100, players=500, worldEdge=4000 | 0.055 ms | 0.1% |
| tickPlayersMoving | npcs=100, players=500, worldEdge=400 | 0.221 ms | 0.2% |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=4000 | 0.083 ms | 0.1% |
| tickPlayersMoving | npcs=1000, players=10, worldEdge=400 | 0.120 ms | 0.1% |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=4000 | 0.152 ms | 0.2% |
| tickPlayersMoving | npcs=1000, players=100, worldEdge=400 | 0.632 ms | 0.6% |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=4000 | 0.373 ms | 0.4% |
| tickPlayersMoving | npcs=1000, players=500, worldEdge=400 | 1.850 ms | 1.8% |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=4000 | 0.799 ms | 0.8% |
| tickPlayersMoving | npcs=10000, players=10, worldEdge=400 | 1.165 ms | 1.2% |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=4000 | 1.755 ms | 1.8% |
| tickPlayersMoving | npcs=10000, players=100, worldEdge=400 | 6.697 ms | 6.7% |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=4000 | 3.446 ms | 3.4% |
| tickPlayersMoving | npcs=10000, players=500, worldEdge=400 | 27.940 ms | 27.9% |
| tickSteadyState | npcs=100, players=10, worldEdge=4000 | 0.008 ms | 0.0% |
| tickSteadyState | npcs=100, players=10, worldEdge=400 | 0.009 ms | 0.0% |
| tickSteadyState | npcs=100, players=100, worldEdge=4000 | 0.010 ms | 0.0% |
| tickSteadyState | npcs=100, players=100, worldEdge=400 | 0.042 ms | 0.0% |
| tickSteadyState | npcs=100, players=500, worldEdge=4000 | 0.030 ms | 0.0% |
| tickSteadyState | npcs=100, players=500, worldEdge=400 | 0.177 ms | 0.2% |
| tickSteadyState | npcs=1000, players=10, worldEdge=4000 | 0.080 ms | 0.1% |
| tickSteadyState | npcs=1000, players=10, worldEdge=400 | 0.094 ms | 0.1% |
| tickSteadyState | npcs=1000, players=100, worldEdge=4000 | 0.074 ms | 0.1% |
| tickSteadyState | npcs=1000, players=100, worldEdge=400 | 0.394 ms | 0.4% |
| tickSteadyState | npcs=1000, players=500, worldEdge=4000 | 0.216 ms | 0.2% |
| tickSteadyState | npcs=1000, players=500, worldEdge=400 | 1.607 ms | 1.6% |
| tickSteadyState | npcs=10000, players=10, worldEdge=4000 | 0.932 ms | 0.9% |
| tickSteadyState | npcs=10000, players=10, worldEdge=400 | 0.969 ms | 1.0% |
| tickSteadyState | npcs=10000, players=100, worldEdge=4000 | 1.657 ms | 1.7% |
| tickSteadyState | npcs=10000, players=100, worldEdge=400 | 4.742 ms | 4.7% |
| tickSteadyState | npcs=10000, players=500, worldEdge=4000 | 3.095 ms | 3.1% |
| tickSteadyState | npcs=10000, players=500, worldEdge=400 | 19.881 ms | 19.9% |

