# The `jacodb-analysis` module

The `jacodb-analysis` module allows launching application dataflow analyses.
It contains an API to write custom analyses, and several ready-to-use analyses.

## Units

The [IFDS](https://dx.doi.org/10.1145/199448.199462) framework is the basis for this module.
To make the implementation scalable, the analyzed code is split into the so-called units, so that the framework 
can analyze them concurrently.
Information is shared between the units via summaries, but the lifecycle of each unit is controlled
separately.

## Get started

The analysis entry point is the [runAnalysis] method. To call it, you have to provide:
* `graph` — an application graph that is used for analysis. To obtain this graph, one should call the [newApplicationGraphForAnalysis] method.
* `unitResolver` — an object that groups methods into units. Choose one from `UnitResolversLibrary`.
Note that, in general, larger units mean more precise but also more resource-consuming analysis.
* `ifdsUnitRunner` — an [IfdsUnitRunner] instance, which is used to analyze each unit. This is what defines concrete analysis.
  Ready-to-use runners are located in `RunnersLibrary`.
* `methods` — a list of methods to analyze.

For example, to detect the unused variables in the given `analyzedClass` methods, you may run the following code
(assuming that `classpath` is an instance of [JcClasspath]):

```kotlin
val applicationGraph = runBlocking { 
    classpath.newApplicationGraphForAnalysis()
}

val methodsToAnalyze = analyzedClass.declaredMethods
val unitResolver = MethodUnitResolver
val runner = UnusedVariableRunner

runAnalysis(applicationGraph, unitResolver, runner, methodsToAnalyze)
```

## Implemented runners

By now, the following runners are implemented:
* `UnusedVariableRunner` that can detect issues like unused variable declaration, unused `return` value, etc.
* `NpeRunner` that can find instructions with possible `null` value dereference.
* Generic `TaintRunner` that can perform taint analysis.
* `SqlInjectionRunner`, which finds places vulnerable to SQL injections, thus performing a specific kind of taint 
  analysis.

## Implementing your own analysis

To implement a simple one-pass analysis, use [IfdsBaseUnitRunner].
To instantiate it, you need an [AnalyzerFactory] instance, which is an object that can create [Analyzer] via
[JcApplicationGraph].

To instantiate an [Analyzer] interface, you have to specify the following:

* `flowFunctions`, which describe the dataflow facts and their transmissions during the analysis;

* how these facts produce vulnerabilities, i.e., you have to implement `getSummaryFacts` and 
  `getSummaryFactsPostIfds` methods.

To implement bidirectional analysis, you may use composite [SequentialBidiIfdsUnitRunner] and [ParallelBidiIfdsUnitRunner].

<!--- MODULE jacodb-analysis -->
<!--- INDEX org.jacodb.analysis -->

[runAnalysis]: https://jacodb.org/docs/jacodb-analysis/org.jacodb.analysis/run-analysis.html
[newApplicationGraphForAnalysis]: https://jacodb.org/docs/jacodb-analysis/org.jacodb.analysis.graph/new-application-graph-for-analysis.html
[IfdsUnitRunner]: https://jacodb.org/docs/jacodb-analysis/org.jacodb.analysis.engine/-ifds-unit-runner/index.html
[JcClasspath]: https://jacodb.org/docs/jacodb-api/org.jacodb.api/-jc-classpath/index.html
[IfdsBaseUnitRunner]: https://jacodb.org/docs/jacodb-analysis/org.jacodb.analysis.engine/-ifds-base-unit-runner/index.html
[AnalyzerFactory]: https://jacodb.org/docs/jacodb-analysis/org.jacodb.analysis.engine/-analyzer-factory/index.html
[Analyzer]: https://jacodb.org/docs/jacodb-analysis/org.jacodb.analysis.engine/-analyzer/index.html
[JcApplicationGraph]: https://jacodb.org/docs/jacodb-api/org.jacodb.api.analysis/-jc-application-graph/index.html
[SequentialBidiIfdsUnitRunner]: https://jacodb.org/docs/jacodb-analysis/org.jacodb.analysis.engine/-sequential-bidi-ifds-unit-runner/index.html
[ParallelBidiIfdsUnitRunner]: https://jacodb.org/docs/jacodb-analysis/org.jacodb.analysis.engine/-parallel-bidi-ifds-unit-runner/index.html