# Java Compilation Database

JCDB is a pure Java library which stores information about Java byte-code located outside the JVM process. Like Reflection do for runtime JCDB do this for byte-code stored in file system.

JCDB uses ASM for reading and parsing jar-files (including build directories) and store information about classes, their hierarchies, methods, annotations etc.

Information is stored in SQLite database which could be in-memory or persistent. Persistence makes data be reused after process is restarted. Accessing persisted data from different processes simultaneously is not supported.

JCDB is ignited by number of jars or directories with build classes:

```kotlin
interface JCDB {

    suspend fun classpath(dirOrJars: List<File>): JcClasspath

    suspend fun load(dirOrJar: File)
    suspend fun load(dirOrJars: List<File>)

    suspend fun refresh()
}
```

`JcClasspath` represents the set of classpath items. Which means that each class should be presented once there.
Otherwise, in case of collision like in jar-hell only one random class will win.

```kotlin
interface JcClasspath {

    val locations: List<ByteCodeLocation>

    fun findClassOrNull(name: String): JcClassOrInterface?
    fun findTypeOrNull(name: String): JcType?
}
```

`findClassOrNull` method will return instance of `JcClass` from `locations` or null otherwise. Where `JcClass` represents
JVM Class:

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

Example of usage:

```kotlin
suspend fun findNormalDistribution(): Any {
    val commonsMath32 = File("commons-math3-3.2.jar")
    val commonsMath36 = File("commons-math3-3.6.1.jar")
    val buildDir = File("my-project/build/classes/java/main")
    val database = jcdb {
        useProcessJRE()
        persistent("/tmp/compilation-db/${System.currentTimeMillis()}")
    }

    // let's index this 3 byte-code sources
    database.load(listOf(commonsMath32, commonsMath36, buildDir))

    // this method just refresh the libraries inside database. If there are any changes in libs then 
    // database just update old data with new results
    database.load(listOf(buildDir))

    // let's assume that we want to get byte-code info only for `commons-math3` version 3.2
    val jcClass = database.classpath(commonsMath32, buildDir)
        .findClass("org.apache.commons.math3.distribution.NormalDistribution")
    println(jcClass.methods.size)
    println(jcClass.constructors.size)
    println(jcClass.annotations.size)

    // here database will call ASM to read method bytecode and return the result
    return jcClass.methods[0].readBody()
}
```

If underling jar file is removed or changed in file system then null will be returned by `readBody` method.
If classpath is inconsistent you may receive `NoClassInClasspathException` in runtime. Database can watch for file system 
changes in a background or refresh jars explicitly:

```kotlin
    val database = jcdb {
        watchFileSystemChanges = true
        useProcessJRE()
        predefinendJars = listOf(lib1, buildDir) 
        persistent()
    }

    // user rebuilds buildDir folder
    // database rereads rebuild directory in a background
```

### Multithreading

Instances of `JcClassOrInterface`, `JcMethod`, `JcClasspath` are thread-safe. Methods of these classes return immutable structures 
and are thread-safe as result. 

`JcClasspath` represents independent snapshot of classes and can't be modified since created. Removing or modifying 
library files will not affect `JcClasspath` instance structure. `JcClasspath#close` method will release all snapshots and will 
clean up persistent store in case of some libraries are outdated.

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

If `JcClasspath` requested with libraries which are not indexed yet, then they will be indexed before and then 
returned new instance of set. 

```kotlin
    val database = jcdb {
        loadByteCode(listOf(lib1))
        persistent()
    }
    
    val cp = database.classpath(buildDir) // database will automatically process buildDir
```

`JCDB` is thread safe. If someone requested `JcClasspath` instance during loading jars from different
thread then `JcClasspath` will be created on a consistent state of loaded jars. That means that jar can't appear in 
`JcClasspath` in partly loaded state. Apart from that there is no guarantee that all submitted for loading jars will be 
loaded.

```kotlin
    val database = jcdb {
        persistent()
    }

    thread(start = true) {
        database.loadJar(listOf(lib1, lib2))            
    }

    thread(start = true) {
        // maybe created when lib2 or both are not loaded into database
        // but buildDir will be loaded anyway
        val cp = database.classpath(buildDir)  
    }
```

### Bytecode loading

Bytecode loading contains two steps:
- retrieve information about classes from jars/build folders 
- classes from jars/build folders are processed (indexes, persist information etc)

`JCDB` or `JcClasspath` instance returned just after first step is done. Final representation of classes is done on second step.

For architecture and other technical information please visit [design page](./design.md)
