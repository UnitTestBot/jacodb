## Overview

Feature is an interface which provide ability to store and query additional information based on bytecode. Feature can
be installed only when instance of `JacoDB` is created.

```kotlin
    val db = jacodb {
        useProcessJRE()
        persistent("/tmp/compilation-db/${System.currentTimeMillis()}") // persist data
        installFeatures(Usages, InMemoryHierarchy)
    }
```

### `InMemoryHierarchy` feature

By default `JacoDB` stores information about class hierarchy in sql database (table `ClassHierarchies` with columns:
class_id, super_id, is_interface).
This brings ability to retrieve whole hierarchy for particular class with recursive sql query. Recursive queries are
quite common and quite slow.

`InMemoryHierarchy` - solves performance problem op built-in solution. It's introduce in-memory cache which fast
searching.

Overhead for memory is O(number of classes).
For own project with about 50K classes (including runtime) memory consumption for such cache is ~6,5Mb of heap memory.

### `Usages` feature

Brings ability to find out places where methods and fields are used.

It's recommended to install `InMemoryHierarchy` for performance purposes

```kotlin
    val db = jacodb {
        useProcessJRE()
        load(allClasspath)
        persistent("/tmp/compilation-db/${System.currentTimeMillis()}") // persist data
        installFeatures(Usages, InMemoryHierarchy)
    }
    val method = run // java.lang.Runnable#run method
    val field = field // java.lang.String#value field
    
    val cp = db.classpath(allClasspath)
    cp.findUsages(method) // sequence of methods which calls `method`
    cp.findUsages(field, FieldUsageMode.READ) // sequence of fields which reads `field` value
```

`Usages` indexer goes through all instructions and collect method calls or fields access and store it into table:

| Column name             | Column description                                                            |
|-------------------------|-------------------------------------------------------------------------------|
| callee_class_symbol_id  | callee class name unique identifier                                           |
| callee_name_symbol_id   | method/field name unique identifier                                           |
| callee_desc_hash        | null for field usage. `Sip` hash of method bytecode `description` for methods |
| opcode                  | instruction operation code                                                    |
| caller_class_symbols_id | caller class name unique identifier                                           |
| caller_method_offsets   | method numbers for this usage                                                 |
| location_id             | location identifier                                                           |

### `Builders` feature

Idea is to find methods that can construct instances of classes like builder pattern or factory methods.

Description of `builder` methods:

- public method
- not getter
- return type is target class or any implementation of target interface
- method parameters do not have references to class. for example, we have `A` class then A, List<A>, Option<A> treated like references to A   

Priorities of matching:

- static methods without parameters matching class name
- static methods without parameters
- has build name
- static method with parameters
- all others

Index data stored in `Builds` table:

| Column name             | Column description                   |
|-------------------------|--------------------------------------|
| class_symbol_id         | caller class name unique identifier  |
| builder_class_symbol_id | builder class name unique identifier |
| priority                | priority of matching                 |
| offset                  | index of method in builder class     |
| location_id             | location identifier                  |

