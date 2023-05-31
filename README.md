[![ci status](https://github.com/UnitTestBot/jacodb/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/UnitTestBot/jacodb/actions/workflows/build-and-test.yml)
[![Maven](https://maven-badges.herokuapp.com/maven-central/org.jacodb/jacodb-api/badge.svg)](https://central.sonatype.com/search?smo=true&q=org.jacodb)
[![Coverage](./docs/badges/jacoco.svg)](https://github.com/UnitTestBot/jacodb/actions/workflows/coverage.yml)


## Overview

`JacoDB` is a pure Java library that allows you to get information about Java bytecode outside the JVM process and to store it in a database. While Java `Reflection` makes it possible to inspect code at runtime, `JacoDB` does the same for bytecode stored in a file system.

`JacoDB` uses [ASM](https://asm.ow2.io/) framework for reading and parsing java bytecode.

Information about classes, hierarchies, annotations, methods, fields, and their usages is stored in SQLite database — either in-memory or persistent. Persisted data can be reused between restarts. Accessing the persistent storage from multiple processes simultaneously is not supported.

## Useful links

- [Design overview and technical information](../../wiki/Design).
- [Full api reference](../../wiki/Api-reference)
- [3-address bytecode representation](../../wiki/Three-address-instruction-list-IR)

## Examples

API has two levels: the one representing in filesystem (**bytecode** and **classes**) and the one appearing at runtime (**types**).

* **bytecode** and **classes** — represent data from `class` files: class with methods, fields, etc. 
* **types** — represent types that can be nullable, parameterized, etc.

Both levels are connected to `JcClasspath`. You can't modify **classes** retrieved from pure bytecode. **types** may be constructed manually by generics substitution.

Java
```java
class Example {
    public static MethodNode findNormalDistribution() throws Exception {
        File commonsMath32 = new File("commons-math3-3.2.jar");
        File commonsMath36 = new File("commons-math3-3.6.1.jar");
        File buildDir = new File("my-project/build/classes/java/main");
        JcDatabase database = JacoDB.async(
                new JcSettings()
                        .useProcessJavaRuntime()
                        .persistent("/tmp/compilation-db/" + System.currentTimeMillis()) // persist data
        ).get();

        // Let's load these three bytecode locations
        database.asyncLoad(Arrays.asList(commonsMath32, commonsMath36, buildDir));

        // This method just refreshes the libraries inside the database. If there are any changes in libs then 
        // the database updates data with the new results.
        database.asyncLoad(Collections.singletonList(buildDir));

        // Let's assume that we want to get bytecode info only for `commons-math3` version 3.2.
        JcClassOrInterface jcClass = database.asyncClasspath(Arrays.asList(commonsMath32, buildDir))
                .get().findClassOrNull("org.apache.commons.math3.distribution.NormalDistribution");
        System.out.println(jcClass.getDeclaredMethods().size());
        System.out.println(jcClass.getAnnotations().size());
        System.out.println(JcClasses.getConstructors(jcClass).size());

        // At this point the database read the method bytecode and return the result.
        return jcClass.getDeclaredMethods().get(0).body();
    }
}
```

Kotlin
```kotlin
suspend fun findNormalDistribution(): Any {
    val commonsMath32 = File("commons-math3-3.2.jar")
    val commonsMath36 = File("commons-math3-3.6.1.jar")
    val buildDir = File("my-project/build/classes/java/main")
    val database = jacodb {
        useProcessJavaRuntime()
        persistent("/tmp/compilation-db/${System.currentTimeMillis()}") // persist data
    }

    // Let's load these three bytecode locations
    database.load(listOf(commonsMath32, commonsMath36, buildDir))

    // This method just refreshes the libraries inside the database. If there are any changes in libs then 
    // the database updates data with the new results.
    database.load(listOf(buildDir))

    // Let's assume that we want to get bytecode info only for `commons-math3` version 3.2.
    val jcClass = database.classpath(listOf(commonsMath32, buildDir))
        .findClass("org.apache.commons.math3.distribution.NormalDistribution")
    println(jcClass.declaredMethods.size)
    println(jcClass.constructors.size)
    println(jcClass.annotations.size)

    // At this point the database read the method bytecode and return the result.
    return jcClass.methods[0].body()
}
```



Note: the `body` method returns `null` if the to-be-processed JAR-file was changed or removed. Class could be in incomplete environment (i.e super class, interface, return type or parameter of method is not found in classpath) then api will throw `NoClassInClasspathException` at runtime. 

The database can watch for file system changes in the background and refresh the JAR-files explicitly:

Java
```java
    public static void watchFileSystem() throws Exception {
        JcDatabase database = JacoDB.async(new JcSettings()
            .watchFileSystem()
            .useProcessJavaRuntime()
            .loadByteCode(Arrays.asList(lib1, buildDir))
            .persistent("", false)).get();
    }


    // A user rebuilds the buildDir folder.
    // The database re-reads the rebuilt directory in the background.
```

Kotlin
```kotlin
    val database = jacodb {
        watchFileSystem()
        useProcessJavaRuntime()
        loadByteCode(listOf(lib1, buildDir))
        persistent("")
    }

    // A user rebuilds the buildDir folder.
    // The database re-reads the rebuilt directory in the background.
```

### Types

**type** can be represented as one of

* primitives
* classes
* arrays
* bounded and unbounded wildcards

It represents runtime behavior according to parameter substitution in the given generic type: 

Java
```java
    public static class A<T> {
        T x = null;
    }
    
    public static class B extends A<String> {
    }

    public static void typesSubstitution() {
        JcClassType b = (JcClassType)classpath.findTypeOrNull("org.jacodb.examples.JavaReadMeExamples.B");
        JcType xType = b.getFields()
                .stream()
                .filter(it -> "x".equals(it.getName()))
                .findFirst().get().getFieldType();
        JcClassType stringType = (JcClassType) classpath.findTypeOrNull("java.lang.String");
        System.out.println(xType.equals(stringType)); // will print `true` 
    }
```

Kotlin
```kotlin
    open class A<T>(val x: T)
    
    class B(x: String): A<String>(x)
    
    suspend fun typesSubstitution() {
        val db = jacodb {
            loadByteCode(listOf(File("all-classpath")))
        }
        val classpath = db.classpath(listOf(File("all-classpath")))
        val b = classpath.findClass<B>().toType()
        println(b.fields.first { it.name == "x"}.fieldType == classpath.findClass<String>().toType()) // will print `true` 
    }
```


## Multithreading

The instances of `JcClassOrInterface`, `JcMethod`, and `JcClasspath` are thread-safe and immutable. 

`JcClasspath` represents an independent snapshot of classes, which cannot be modified since it is created. Removing or modifying library files does not affect `JcClasspath` instance structure. The `JcClasspath#close` method releases all snapshots and cleans up the persisted data if some libraries are outdated.

Java
```java
    public static void refresh() throws Exception {
        JcDatabase database = JacoDB.async(
            new JcSettings()
            .watchFileSystem()
            .useProcessJavaRuntime()
            .loadByteCode(Arrays.asList(lib1, buildDir))
            .persistent("...")
        ).get();

        JcClasspath cp = database.asyncClasspath(Collections.singletonList(buildDir)).get();
        database.asyncRefresh().get(); // does not affect cp classes

        JcClasspath cp1 = database.asyncClasspath(Collections.singletonList(buildDir)).get(); // will use new version of compiled results in buildDir
    }
```


Kotlin
```kotlin
    val database = jacodb {
        watchFileSystem()
        useProcessJavaRuntime()
        loadByteCode(listOf(lib1, buildDir))
        persistent("")
    }
    
    val cp = database.classpath(listOf(buildDir))
    database.refresh() // does not affect cp classes
    
    val cp1 = database.classpath(listOf(buildDir)) // will use new version of compiled results in buildDir
```

If there is a request for a `JcClasspath` instance containing the libraries, which haven't been indexed yet, the indexing process is triggered and the new instance of the `JcClasspath` set is returned. 

Java
```java
    public static void autoProcessing() throws Exception {
        JcDatabase database = JacoDB.async(
            new JcSettings()
                .loadByteCode(Arrays.asList(lib1))
                .persistent("...")
        ).get();

        JcClasspath cp = database.asyncClasspath(Collections.singletonList(buildDir)).get(); // database will automatically process buildDir

    }
```

Kotlin 
```kotlin
    val database = jacodb {
        loadByteCode(listOf(lib1))
        persistent("")
    }
    
    val cp = database.classpath(listOf(buildDir)) // database will automatically process buildDir
```

`JacoDB` is thread-safe. If one requests `JcClasspath` instance while loading JAR-files from another thread, 
`JcClasspath` can represent only a consistent state of the JAR-files being loaded. It is the completely loaded 
JAR-file that appears in `JcClasspath`. Please note: there is no guarantee that all the JAR-files, submitted for loading, will be actually loaded.

Java

```java
class Example {
    public static void main(String[] args) {
        val db = JacoDB.async(new JcSettings()).get();

        new Thread(() -> db.asyncLoad(Arrays.asList(lib1, lib2)).get()).start();

        new Thread(() -> {
            // maybe created when lib2 or both are not loaded into database
            // but buildDir will be loaded anyway
            var cp = db.asyncClasspath(buildDir).get();
        }).start();
    }
}
 ```

Kotlin
```kotlin
    val db = jacodb {
        persistent("")
    }
    
    thread(start = true) {
        runBlocking {
            db.load(listOf(lib1, lib2))
        }
    }
    
    thread(start = true) {
        runBlocking {
            // maybe created when lib2 or both are not loaded into database
            // but buildDir will be loaded anyway
            val cp = db.classpath(listOf(buildDir))
        }
    }
```

### Bytecode loading

Bytecode loading consists of two steps:

* retrieving information about the class names from the JAR-files or build directories
* reading **classes** bytecode from the JAR-files or build directories and processing it (persisting data, setting up `JcFeature` implementations, etc.)

`JacoDB` or `JcClasspath` instances are returned right after the first step is performed. You retrieve the final representation of **classes** during the second step. It is possible that the `.class` files undergo changes at some moment between the first step and the second, and **classes** representation is affected accordingly.

Here are [Benchmarks](../../wiki/Benchmarks) results and [Soot migration](../../wiki/Soot-migration).


