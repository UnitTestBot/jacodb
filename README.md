# Java Compilation Database

Java Compilation Database is a pure Java database which stores information about Java compiled byte-code located outside
the JVM process. Like `Reflection` do this for runtime Java Compilation Database is doing this for byte-code stored 
somewhere in file system.

Java Compilation Database uses ASM for reading and parsing jar-files (including build directories) and store information about
classes, their hierarchies, methods, annotations etc.

Database could be in-memory or persistent. In-memory means that it can be used as a cache only. Persistent stores data on disk 
and makes data be reused after process is restarted. By design Java Compilation Database do not support accessing disk 
stored data from different processes simultaneously.

## Design

Java Compilation Database is ignited by number of jars or directories with build classes:

```kotlin
interface CompilationDatabase {

    suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet

    suspend fun load(dirOrJar: File)
    suspend fun load(dirOrJars: List<File>)

    suspend fun refresh()

    fun watchFileSystemChanges()
}
```

`ClasspathSet` represents the set of classpath items. Which means that each class should be presented once there.
Otherwise, in case of collission like in jar-hell only one random class will win.

```kotlin
interface ClasspathSet {

    val locations: List<ByteCodeLocation>

    suspend fun findClassOrNull(name: String): ClassId?

    suspend fun findSubTypesOf(name: String): List<ClassId>
}
```

`findClass` method will return instance of `ClassId` found in `locations` or null otherwise. Where `ClassId` represents
JVM Class:

```kotlin
interface ClassId {

    val location: ByteCodeLocation

    val name: String
    val simpleName: String

    suspend fun methods(): List<MethodId>

    suspend fun superclass(): ClassId?
    suspend fun interfaces(): List<ClassId>
    suspend fun annotations(): List<ClassId>

}

interface MethodId {
    val name: String

    val classId: ClassId
    suspend fun returnType(): ClassId?
    suspend fun parameters(): List<ClassId>
    suspend fun annotations(): List<ClassId>

    suspend fun readBody(): MethodNode
}
```

Example of usage:

```kotlin
suspend fun findNormalDistribution(): Any {
    val commonsMath32 = File("commons-math3-3.2.jar")
    val commonsMath36 = File("commons-math3-3.6.1.jar")
    val buildDir = File("my-project/build/classes/java/main")
    val database = compilationDatabase {
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
    val classId = database.classpathSet(commonsMath32, buildDir)
        .findClass("org.apache.commons.math3.distribution.NormalDistribution")
    println(classId.methods.size)
    println(classId.constructors.size)
    println(classId.annotations.size)

    // here database will call ASM to read method bytecode and return the result
    return classId.methods[0].readBody()
}
```

If underling jar file is removed from file system then exception will be thrown only when method `readBody` is called.
If classpath is inconsistent you may receive `ClassNotFoundException` in runtime. Database can watch for file system 
changes in a background or refresh jars explicitly:

```kotlin
    val database = compilationDatabase {
        watchFileSystemChanges = true
        predefinendJars = listOf(lib1, buildDir) 
        persistent()
    }

    // user rebuilds buildDir folder
    // database rereads rebuild directory in a background
```

### Multithreading

Instances of `ClassId`, `MethodId`, `ClasspathSet` are thread-safe. Methods of these classes return immutable structures 
and are thread-safe as result. 

`ClasspathSet` represents independent snapshot of classes and can't be modified since created. Removing or modifying 
library files will not affect `ClasspathSet` instance structure. `ClasspathSet#close` method will release all snapshots and will 
clean up persistent store in case of some libraries are outdated.

```kotlin
    val database = compilationDatabase {
        watchFileSystemChanges()
        predefinedDirOrJars = listOf(lib1, buildDir)
        persistent()
    }
    
    val cp = database.classpathSet(buildDir)
    database.refresh() // does not affect cp classes

    val cp1 = database.classpathSet(buildDir) // will use new version of compiled results in buildDir
```

If `ClasspathSet` requested with libraries which are not indexed yet, then they will be indexed before and then 
returned new instance of set. 

```kotlin
    val database = compilationDatabase {
        watchFileSystemChanges()
        predefinedDirOrJars = listOf(lib1)
        persistent()
    }
    
    val cp = database.classpathSet(buildDir) // database will automatically process buildDir
```

`CompilationDatabase` is thread safe. If someone requested `ClasspathSet` instance during loading jars from different
thread then `ClasspathSet` will be created on a consistent state of loaded jars. That means that jar can't appear in 
`ClasspathSet` in partly loaded state. Apart from that there is no guarantee that all submitted for loading jars will be 
loaded.

```kotlin
    val database = compilationDatabase {
        persistent()
    }

    thread(start = true) {
        database.loadJar(listOf(lib1, lib2))            
    }

    thread(start = true) {
        // maybe created when lib2 or both are not loaded into database
        // but buildDir will be loaded anyway
        val cp = database.classpathSet(buildDir)  
    }
```
