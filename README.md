# Java Compilation Database

Java Compilation Database is a pure Java database which stores information about Java compiled byte-code located outside
the JVM process. Like `Reflection` do this for runtime Java Compilation Database is doing this for byte-code stored 
somewhere in file system.

Java Compilation Database uses ASM for reading and parsing jar-files (including build directories) and store information about
classes, their hierarchies, methods, annotations etc.

Database could be in-memory or persistent. In-memory means that it can be used as a cache only. Persistent stores data on disk 
and makes data be reused after process is restarted. By design Java Compilation Database do not support accessing disk 
stored data from different processes simultaneously.

Java Compilation Database is ignited by number of jars or directories with build classes:

```kotlin
interface JCDB {

    suspend fun JcClasspath(dirOrJars: List<File>): JcClasspath

    suspend fun load(dirOrJar: File)
    suspend fun load(dirOrJars: List<File>)

    suspend fun refresh()

    fun watchFileSystemChanges()
}
```

`JcClasspath` represents the set of classpath items. Which means that each class should be presented once there.
Otherwise, in case of collision like in jar-hell only one random class will win.

```kotlin
interface JcClasspath {

    val locations: List<ByteCodeLocation>

    fun findClassOrNull(name: String): JcClassOrInterface?

    fun findSubTypesOf(name: String): List<JcClassOrInterface>
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

    val jcClass: JcClassOrInterface
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
        persistent {
            location = "/tmp/compilation-db/${System.currentTimeMillis()}"
        }
    }

    // let's index this 3 byte-code sources
    database.loadJars(listOf(commonsMath32, commonsMath36, buildDir))

    // this method just refresh the libraries inside database. If there are any changes in libs then 
    // database just update old data with new results
    database.loadJars(listOf(buildDir))

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

If underling jar file is removed or changed in file system then null will be returned `readBody` method.
If classpath is inconsistent you may receive `ClassNotFoundException` in runtime. Database can watch for file system 
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

Instances of `JcClass`, `JcMethod`, `JcClasspath` are thread-safe. Methods of these classes return immutable structures 
and are thread-safe as result. 

`JcClasspath` represents independent snapshot of classes and can't be modified since created. Removing or modifying 
library files will not affect `JcClasspath` instance structure. `JcClasspath#close` method will release all snapshots and will 
clean up persistent store in case of some libraries are outdated.

```kotlin
    val database = jcdb {
        watchFileSystemChanges()
        useProcessJRE()
        predefinedDirOrJars = listOf(lib1, buildDir)
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
        watchFileSystemChanges()
        useProcessJRE()
        predefinedDirOrJars = listOf(lib1)
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

For architecture and other technical information please visit [design page](./design.md)
