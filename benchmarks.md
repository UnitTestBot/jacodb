### Environment

|           |                                                |
|-----------|------------------------------------------------|
| OS        | Windows 10 Pro                                 |
| Processor | 11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz |
| RAM       | 16,0 GB                                        |
| Disk      | SSD                                            | 

Benchmark runs across different scopes of java bytecode with following meanings:
- runtime - only java runtime without any additional dependencies
- runtime + guava - java runtime with one jar for guava 
- runtime + project classpath - java runtime with all visible dependencies of `jcdb` project
- runtime + Idea community - java runtime with all visible dependencies of `IDEA community` project

`JCDB` benchmarks also include scheme when `Usages` feature is installed 

### JCDB

```ssh
./gradlew jcdbBenchmark
```


| Benchmark                                                                                                                                 | Repeats  | Avg time per operation  | 
|-------------------------------------------------------------------------------------------------------------------------------------------|----------|-------------------------|
| [runtime](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbBenchmarks.kt#L36)                                   | 5        | 741 ms                  |
| [runtime + guava](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbBenchmarks.kt#L76)                           | 5        | 740 ms                  |
| [runtime + project dependencies](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbBenchmarks.kt#L55)            | 5        | 1034 ms                 |
| [runtime + IDEA community dependecies](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbBenchmarks.kt#L97)      | 5        | 2324 ms                 |
| [runtime + `Usages`](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbBenchmarks.kt#L45)                        | 5        | 896 ms                  |
| [runtime + guava + `Usages`](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbBenchmarks.kt#L86)                | 5        | 929 ms                  |
| [runtime + project dependencies + `Usages`](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbBenchmarks.kt#L65) | 5        | 1298 ms                 |

### Soot

Run
```ssh
./gradlew sootBenchmark
```

| Benchmark                                                                                                                            | Repeats  | Avg time per operation  |
|--------------------------------------------------------------------------------------------------------------------------------------|----------|-------------------------|
| [runtime](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/SootBenchmarks.kt#L53)                              | 5        | 827 ms                  |
| [runtime + guava](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/SootBenchmarks.kt#L58)                      | 5        | 733 ms                  |
| [runtime + project dependencies](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/SootBenchmarks.kt#L63)       | 5        | 1839 ms                 |
| [runtime + IDEA community dependecies](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/SootBenchmarks.kt#L68) | 5        | 10091 ms                |


### Soot comparison and differences

Soot just read all jar files and stores bytecode in memory. Jcdb works in a bit different way. Jcdb read available .class from jars and build folders in a parallel and as a result 
is faster in a situation when there are a lot of jar files (because of parallel execution). Keep in mind that Jcdb has a huge job processed 
in a background while almost all api is ready (hierarchy and usages requires background activity to be finished).

Jcdb performance for background activities without `Usages` feature:

```ssh
./gradlew awaitBackgroundBenchmark
```
JcdbAwaitBackgroundBenchmarks

| Benchmark                                                                                                                                                                      | Repeats  | Avg time per operation  |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|-------------------------|
| [runtime: wait for background jobs](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbAwaitBackgroundBenchmarks.kt#L59)                               | 5        | 3182 ms                 |
| [runtime + project dependencies: wait for background jobs](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbAwaitBackgroundBenchmarks.kt#L72)        | 5        | 6696 ms                 |
| [runtime + IDEA community dependencies: wait for background jobs](../blob/main/jcdb-core/src/test/kotlin/org/utbot/jcdb/impl/performance/JcdbAwaitBackgroundBenchmarks.kt#L86) | 2        | 76734 ms                |

For `Idea Community` code base result sqlite database file size is ~3.5Gb




