### Environment

|           |                                                |
|-----------|------------------------------------------------|
| OS        | Windows 10 Pro                                 |
| Processor | 11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz |
| RAM       | 16,0 GB                                        |
| Disk      | SSD                                            | 

### JCDB

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


### Soot comparison and differences

Soot just read all jar files and stores bytecode in memory. Jcdb works in a bit different way. Jcdb read available .class from jars and build folders in a parallel and as a result 
is faster in a situation when there are a lot of jar files (because of parallel execution). Keep in mind that Jcdb has a huge job processed 
in a background while almost all api is ready (hierarchy and usages requires background activity to be finished).

Jcdb performance for background activities without `Usages` feature:

```ssh
./gradlew awaitBackgroundBenchmark
```


| Benchmark                                            | Mode | Cnt | Score     | Error      | Units |
|------------------------------------------------------|------|-----|-----------|------------|-------|
| JcdbJvmBackgroundBenchmarks.awaitBackground          | avgt | 5   | 3182.435  | 457.025    | ms/op |
| JcdbAllClasspathBackgroundBenchmarks.awaitBackground | avgt | 5   | 6696.840  | 1508.409   | ms/op |
| JcdbIdeaBackgroundBenchmarks.awaitBackground         | avgt | 2   | 76734.555 |            | ms/op |

For `Idea Community` code base result sqlite database file size is ~3.5Gb




