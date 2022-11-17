## Overview

Feature is an interface which provide ability to store and query additional information based on bytecode. Feature can be installed only when instance of `JCDB` is created.

```kotlin
    val db = jcdb {
        useProcessJRE()
        persistent("/tmp/compilation-db/${System.currentTimeMillis()}") // persist data
        installFeatures(Usages, InMemoryHierarchy)
    }
```


### `InMemoryHierarchy` feature

By default `JCDB` stores information about class hierarchy in sql database (table `ClassHierarchies` with columns: class_id, super_id, is_interface).
This brings ability to retrieve whole hierarchy for particular class with recursive sql query. Recursive queries are quite common and quite slow.

`InMemoryHierarchy` - solves performance problem op built-in solution. It's introduce in-memory cache which fast searching. 

Overhead for memory is O(number of classes).
For own project with about 50K classes (including runtime) memory consumption for such cache is ~6,5Mb of heap memory.

### `Usages` feature

Brings ability to find out places where methods and fields are used.

It's recommended to install `InMemoryHierarchy` for performance purposes

```kotlin
    val db = jcdb {
        useProcessJRE()
        load(allClasspath)
        persistent("/tmp/compilation-db/${System.currentTimeMillis()}") // persist data
        installFeatures(Usages, InMemoryHierarchy)
    }
    val method = run // java.lang.Runnable::run method
    val field = field // java.lang.Runnable::run method

    val cp = db.classpath(allClasspath)
    cp.findUsages(method) // sequence of methods which calls `method`
    cp.findUsages(field, FieldUsageMode.READ) // sequence of fields which reads `field` value
```

`Usages` indexer goes through all instructions and collect method calls or fields access and store it into table 

| Column name            | Column description                                             |
|------------------------|----------------------------------------------------------------|
| callee_class_hash      | callee class name hash                                         |
| callee_name            | method/field name                                              |
| callee_desc_hash       | null for field usage. method bytecode `description` for methods |
| opcode                 | instruction operation code                                     |
| caller_class_hash      | caller class name hash                                         |
| caller_method_offsets  | caller method                                                  |
| location_id            | location identifier                                            |




