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


| Benchmark                                           | Repeats | Avg time per operation | 
|-----------------------------------------------------|---------|------------------------|
| JcdbBenchmarks.jvmRuntime                           | 5       | 741 ms                 |
| JcdbBenchmarks.jvmRuntimeWithUsages                 | 5       | 896 ms                 |
| JcdbBenchmarks.jvmRuntimeWithGuava                  | 5       | 740 ms                 |
| JcdbBenchmarks.jvmRuntimeWithGuavaWithUsages        | 5       | 929 ms                 |
| JcdbBenchmarks.jvmRuntimeWithAllClasspath           | 5       | 1034 ms                |
| JcdbBenchmarks.jvmRuntimeWithAllClasspathWithUsages | 5       | 1298 ms                |
| JcdbBenchmarks.jvmRuntimeWithIdeaCommunity          | 5       | 2324 ms                |

### Soot

Run
```ssh
./gradlew sootBenchmark
```

| Benchmark                                  | Repeats  | Avg time per operation  |
|--------------------------------------------|----------|-------------------------|
| SootBenchmarks.jvmRuntime                  | 5        | 827 ms                  |
| SootBenchmarks.jvmRuntimeWithGuava         | 5        | 733 ms                  |
| SootBenchmarks.jvmRuntimeWithAllClasspath  | 5        | 1839 ms                 |
| SootBenchmarks.jvmRuntimeWithIdeaCommunity | 5        | 10091 ms                |


### Soot comparison and differences

Soot just read all jar files and stores bytecode in memory. Jcdb works in a bit different way. Jcdb read available .class from jars and build folders in a parallel and as a result 
is faster in a situation when there are a lot of jar files (because of parallel execution). Keep in mind that Jcdb has a huge job processed 
in a background while almost all api is ready (hierarchy and usages requires background activity to be finished).

Jcdb performance for background activities without `Usages` feature:

```ssh
./gradlew awaitBackgroundBenchmark
```


| Benchmark                                            | Repeats  | Avg time per operation  |
|------------------------------------------------------|----------|-------------------------|
| JcdbJvmBackgroundBenchmarks.awaitBackground          | 5        | 3182 ms                 |
| JcdbAllClasspathBackgroundBenchmarks.awaitBackground | 5        | 6696 ms                 |
| JcdbIdeaBackgroundBenchmarks.awaitBackground         | 2        | 76734 ms                |

For `Idea Community` code base result sqlite database file size is ~3.5Gb




