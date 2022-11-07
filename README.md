`JCDB` is a pure Java library that allows you to get information about Java bytecode outside the JVM process and to store it in a database. While Java `Reflection` makes it possible to inspect code at runtime, `JCDB` does the same for bytecode stored in a file system.

`JCDB` uses [ASM](https://asm.ow2.io/) framework for reading and parsing JAR-files as well as build directories.

Information about classes, hierarchies, annotations, methods, fields, and their usages is stored in SQLite database — either in-memory or persistent. Persisted data can be reused between restarts. Accessing the persistent storage from multiple processes simultaneously is not supported.

To start creating database, `JCDB` gets a list of JAR-files or build directories:

```kotlin
interface JCDB {

    suspend fun classpath(dirOrJars: List<File>): JcClasspath

    suspend fun load(dirOrJar: File)
    suspend fun load(dirOrJars: List<File>)

    suspend fun refresh()
}
```

`JcClasspath` represents the set of classpath items. Each item occurs only once here — otherwise, in case of collision, like in JAR hell, only one random class could win.

```kotlin
interface JcClasspath {

    val locations: List<ByteCodeLocation>

    fun findClassOrNull(name: String): JcClassOrInterface?
    fun findTypeOrNull(name: String): JcType?
}
```

## API

Bytecode has two representations: the one stored in filesystem (**classes**) and the one appearing at runtime (**types**).

* **classes** — represent data from `.class` files as is. Each `.class` file is parsed with ASM library and represented as ASM `ClassNode`.
* **types** — represent types that can be nullable, parameterized, etc.

Both levels are connected to `JcClasspath` to avoid JAR hell.
You can't modify **classes** retrieved from pure bytecode. **types** may be constructed manually by parameterizing generics.


### Classes

`JcClasspath#findClassOrNull` returns an instance of `JcClassOrInterface` from `locations`. If there is no class in `locations`, `findClassOrNull` returns `null`.

`JcClassOrInterface` represents JVM bytecode:

```kotlin
interface JcClassOrInterface {

    val location: ByteCodeLocation

    val name: String
    val simpleName: String

    val methods: List<JcMethod>
    val fields: List<JcField>

    val superClass: JcClassOrInterface?
    val interfaces: List<JcClassOrInterface>
    val interClasses: List<JcClassOrInterface>
    val outerClass: JcClassOrInterface?

}

interface JcMethod {
    val name: String

    val enclosingClass: JcClassOrInterface
    val returnClass: JcClassOrInterface?
    val parameters: List<JcParameter>

    fun body(): MethodNode?
}
```

Usage example:

```kotlin
suspend fun findNormalDistribution(): Any {
    val commonsMath32 = File("commons-math3-3.2.jar")
    val commonsMath36 = File("commons-math3-3.6.1.jar")
    val buildDir = File("my-project/build/classes/java/main")
    val database = jcdb {
        useProcessJRE()
        persistent("/tmp/compilation-db/${System.currentTimeMillis()}")
    }

    // Let's index these three bytecode sources.
    database.load(listOf(commonsMath32, commonsMath36, buildDir))

    // This method just refreshes the libraries inside the database. If there are any changes in libs then 
    // the database updates data with the new results.
    database.load(listOf(buildDir))

    // Let's assume that we want to get bytecode info only for `commons-math3` version 3.2.
    val jcClass = database.classpath(commonsMath32, buildDir)
        .findClass("org.apache.commons.math3.distribution.NormalDistribution")
    println(jcClass.methods.size)
    println(jcClass.constructors.size)
    println(jcClass.annotations.size)

    // At this point the database calls ASM to read the method bytecode and to return the result.
    return jcClass.methods[0].body()
}
```

Note: the `body` method returns `null` if the to-be-processed JAR-file was changed or removed.

If a classpath item is inappropriate you may receive `NoClassInClasspathException` at runtime. The database can watch 
for file system changes in the background and refresh the JAR-files explicitly:

```kotlin
    val database = jcdb {
        watchFileSystemChanges = true
        useProcessJRE()
        loadByteCode(listOf(lib1, buildDir)) 
        persistent()
    }

    // A user rebuilds the buildDir folder.
    // The database re-reads the rebuilt directory in the background.
```

### Types

**types** representation includes information on

* primitive types
* classes
* arrays
* bounded and unbounded wildcards

It represents runtime behavior according to parameter substitution in the given generic type: 

```kotlin
    open class A<T> {
        val x: T
    }

    class B: A<String>()

    fun main() {
        val b = classpath.findClass<B>().toType()
        println(b.fields.first { it.name == "x"}.fieldType == cp.findClass<String>().toType()) // will print `true` 
    }

```


## Multithreading

The instances of `JcClassOrInterface`, `JcMethod`, and `JcClasspath` are thread-safe and immutable. 

`JcClasspath` represents an independent snapshot of classes, which cannot be modified since it is created. Removing or modifying library files does not affect `JcClasspath` instance structure. The `JcClasspath#close` method releases all snapshots and cleans up the persisted data if some libraries are outdated.

```kotlin
    val database = jcdb {
        watchFileSystemChanges()
        useProcessJavaRuntime()
        loadByteCode(listOf(lib1, buildDir))
        persistent()
    }
    
    val cp = database.classpath(buildDir)
    database.refresh() // does not affect cp classes

    val cp1 = database.classpath(buildDir) // will use new version of compiled results in buildDir
```

If there is a request for a `JcClasspath` instance containing the libraries, which haven't been indexed yet, the indexing process is triggered and the new instance of the `JcClasspath` set is returned. 

```kotlin
    val database = jcdb {
        loadByteCode(listOf(lib1))
        persistent()
    }
    
    val cp = database.classpath(buildDir) // database will automatically process buildDir
```

`JCDB` is thread-safe. If one requests `JcClasspath` instance while loading JAR-files from another thread, 
`JcClasspath` can represent only a consistent state of the JAR-files being loaded. It is the completely loaded 
JAR-file that appears in `JcClasspath`. Please note: there is no guarantee that all the JAR-files, submitted for loading, will be actually loaded.

```kotlin
    val db = jcdb {
        persistent()
    }
    
    thread(start = true) {
        db.load(listOf(lib1, lib2))
    }
    
    thread(start = true) {
        // maybe created when lib2 or both are not loaded into database
        // but buildDir will be loaded anyway
        val cp = db.classpath(buildDir)
    }
```

### Bytecode loading

Bytecode loading consists of two steps:

* retrieving information about the class names from the JAR-files or build directories
* reading **classes** bytecode from the JAR-files or build directories and processing it (persisting data, setting up `JcFeature` implementations, etc.)

`JCDB` or `JcClasspath` instances are returned right after the first step is performed. You retrieve the final representation of **classes** during the second step. It is possible that the `.class` files undergo changes at some moment between the first step and the second, and **classes** representation is affected accordingly.

For architecture and other technical information please visit the [design page](./design.md).
