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

    suspend fun classpathSet(locations: List<File>): ClasspathSet

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

    suspend fun findClass(name: String): ClassId?
}
```

`findClass` method will return instance of `ClassId` found in `locations` or null otherwise. Where `ClassId` represents
JVM Class:

```kotlin
interface ClassId {

    val location: ByteCodeLocation

    val name: String
    val simpleName: String

    val methods: List<MethodId>

    val parents: List<ClassId>
    val interfaces: List<ClassId>
    val annotations: List<ClassId>
}
```

Example of usage:

```kotlin
suspend fun findNormalDistribution(): Any {
    val commonsMath32 = File("commons-math3-3.2.jar")
    val commonsMath36 = File("commons-math3-3.6.1.jar")
    val buildDir = File("my-project/build/classes/java/main")
    val database = compilationDatabase {
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
Database can watch for file system changes in a background or refresh jars explicitly:

```kotlin
    val database = compilationDatabase {
        watchFileSystemChanges = true
        predefinendJars = listOf(lib1, buildDir) 
        persistent()
    }

    // user rebuilds buildDir folder
    // database rereads rebuild directory in a background
```

### Open questions

Should we sync changes from database to ClasspathSet?

```kotlin
    val database = compilationDatabase {
        watchFileSystemChanges = true
        predefinendJars = listOf(lib1, buildDir)
        persistent()
    }
    
    val cp = database.classpathSet(buildDir)
    database.refresh() // does this call updates state of cp?
```
