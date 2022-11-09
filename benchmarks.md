### Environment

|           |                                                |
|-----------|------------------------------------------------|
| OS        | Windows 10 Pro                                 |
| Processor | 11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz |
| RAM       | 16,0 GB                                        |
| Disk      | SSD                                            | 

### JCDB

Run
```ssh
./gradlew jcdbBenchmark
```


| Benchmark                                           | Mode | Cnt | Score    | Error    | Units  |
|-----------------------------------------------------|------|-----|----------|----------|--------|
| JcdbBenchmarks.jvmRuntime                           | avgt | 5   | 741.948  | 427.314  | ms/op  |
| JcdbBenchmarks.jvmRuntimeWithUsages                 | avgt | 5   | 896.083  | 546.330  | ms/op  |
| JcdbBenchmarks.jvmRuntimeWithGuava                  | avgt | 5   | 740.713  | 514.594  | ms/op  |
| JcdbBenchmarks.jvmRuntimeWithGuavaWithUsages        | avgt | 5   | 929.675  | 774.685  | ms/op  |
| JcdbBenchmarks.jvmRuntimeWithAllClasspath           | avgt | 5   | 1034.280 | 1425.528 | ms/op  |
| JcdbBenchmarks.jvmRuntimeWithAllClasspathWithUsages | avgt | 5   | 1298.637 | 1355.756 | ms/op  |
| JcdbBenchmarks.jvmRuntimeWithIdeaCommunity          | avgt | 5   | 2324.834 | 1324.016 | ms/op  |

### Soot

Run
```ssh
./gradlew sootBenchmark
```

| Benchmark                                  | Mode | Cnt | Score     | Error    | Units  |
|--------------------------------------------|------|-----|-----------|----------|--------|
| SootBenchmarks.jvmRuntime                  | avgt | 5   | 827.527   | 225.962  | ms/op  |
| SootBenchmarks.jvmRuntimeWithGuava         | avgt | 5   | 733.182   | 262.707  | ms/op  |
| SootBenchmarks.jvmRuntimeWithAllClasspath  | avgt | 5   | 1839.610  | 186.369  | ms/op  |
| SootBenchmarks.jvmRuntimeWithIdeaCommunity | avgt | 5   | 10091.841 | 1233.834 | ms/op  |


### Comparison


