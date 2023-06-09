# Module jacodb-api

## Settings 

The [JcSettings] class is used for creating a [JcDatabase] instance.

Use custom Java runtime (not the current process) for bytecode analysis.

There are two shortcuts: [JcSettings].[useProcessJavaRuntime] is for using current process runtime (the default option), and
[JcSettings].[useJavaHomeRuntime] is for using Java runtime from the JAVA_HOME environment variable.

[JcDatabase] instance is created based on settings. If an instance is not needed, the `close` method should be
called.

## Features 

[JcFeature] is an additional feature which can collect data from bytecode, persist it in database and use it to find specific places.
[JcFeature] has parameterization of request/response types.

A feature lifecycle:
- Pre-indexing — can be used to prepare a database scheme, for example.
- Indexing.
- Flushing indexed data to a persistent storage.
- Post-indexing step — can be used to set up the database-specific indexes.
- Updating indexes (if a bytecode location is outdated and removed).

Call on each step of a lifecycle with respected signal.

| property | type       | description                                                     |
|----------|------------|-----------------------------------------------------------------|
| signal   | [JcSignal] | [BeforeIndexing], [AfterIndexing], [LocationRemoved], or [Drop] |

<!--- MODULE jacodb-api -->
<!--- INDEX org.jacodb.api -->

[JcSettings]: https://jacodb.org/docs/jacodb-core/org.jacodb.impl/-jc-settings/index.html
[useProcessJavaRuntime]: https://jacodb.org/docs/jacodb-core/org.jacodb.impl/-jc-settings/use-process-java-runtime.html
[useJavaHomeRuntime]: https://jacodb.org/docs/jacodb-core/org.jacodb.impl/-jc-settings/use-java-home-runtime.html
[JcDatabase]: https://jacodb.org/docs/jacodb-api/org.jacodb.api/-jc-database/index.html
[JcDatabase]: https://jacodb.org/docs/jacodb-api/org.jacodb.api/-jc-database/index.html
[JcFeature]: https://jacodb.org/docs/jacodb-api/org.jacodb.api/-jc-feature/index.html
[JcSignal]: https://jacodb.org/docs/jacodb-api/org.jacodb.api/-jc-signal/index.html
[BeforeIndexing]: https://jacodb.org/docs/jacodb-api/org.jacodb.api/-jc-signal/-before-indexing/index.html
[AfterIndexing]: https://jacodb.org/docs/jacodb-api/org.jacodb.api/-jc-signal/-after-indexing/index.html
[LocationRemoved]: https://jacodb.org/docs/jacodb-api/org.jacodb.api/-jc-signal/-location-removed/index.html
[Drop]: https://jacodb.org/docs/jacodb-api/jacodb-api/org.jacodb.api/-jc-signal/-drop/index.html







