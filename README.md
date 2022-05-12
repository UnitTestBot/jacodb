## Java Compilation Database

### Database
Compilation database saves information about bytecode: classes, they hierarchies, methods, annotations etc.

Expected two types of stores:
- non persistent (for cli)
- persistent (for ide plugin)

[Database](https://github.com/UnitTestBot/JavaCompilationDatabase/blob/master/src/main/kotlin/com/huawei/java/compilation/database/api/Api.kt#L44) ignited by jar files and by 
folders with compiled `.class` files (build folders). 

Database:
- has ability to add compiled code, refresh it state (re-read compiled classes).
- only stores metadata about top level byte-code: classes, methods, annotations.
- can watch for file system changes or be refreshed explicitly.
- allows concurrent access

### Classpath modules
Each module in project may have dedicated classpath. `CompilationDatabase#classpathSet` method return `ClasspathSet` instance which brings 
ability to resolve `DatabaseClass` by its classname and then retrieve information about methods etc.

### Database classes and methods
[DatabaseClass](https://github.com/UnitTestBot/JavaCompilationDatabase/blob/master/src/main/kotlin/com/huawei/java/compilation/database/api/Api.kt#L5) represents
all necessary metadata information:
- annotations
- interfaces
- fields (?)
- list of [DatabaseClassMethod](https://github.com/UnitTestBot/JavaCompilationDatabase/blob/master/src/main/kotlin/com/huawei/java/compilation/database/api/Api.kt#L26)

`DatabaseClassMethod#readBody` provides ASM structure for method bytecode. When this method is called ASM reads bytecode from file system.

## Non persistent store

All information placed in memory and do not stored on disk.

## Persistent store

All information stored on disk with possible clusterisation for each library/build folder.
