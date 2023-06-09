# Module jacodb-core

## In-memory hierarchy

[InMemoryHierarchy] bootstrap hierarchy search based on in-memory caching of structures

## Usages (aka Call Graph)

[Usages] allows to find field and method usages:

```kotlin
    val db = jacodb {
        loadByteCode(allClasspath) // all classpath of current process
        installFeatures(Usages, InMemoryHierarchy) // without that line memory footprint will be low as well as performance
    }
    val classpath = db.classpath(allClasspath)
    val extension = classpath.usagesExtension()
    val runMethod = classpath.findClassOrNull("java.lang.Runnable")!!.declaredMethods.first() // find run method
    
    extension.findUsages(runMethod) // all implementations of Runnable#run method
    val systemOutField = cp.findClass("java.lang.System")!!.declaredFields.first { it.name == "out" }
    val result = ext.findUsages(systemOutField, FieldUsageMode.READ).toList()
```

## Builders (experimental)

[Builders] find and prioritize methods that may be used for a particular class instance. The feature heuristics are:
- the method is public
- the method's return type should correspond to the given class or one of its subclasses
- method parameters should not reference the given class in any way (i.e., we are looking for _class A_, then _A_, _ListA_, _B extends A_ are prohibited)

Priorities are:
1. Static class with no parameters
2. Static class with parameters
3. Non-static class with a `build` name
4. Other

```kotlin
    val extension = classpath.buildersExtension()
    // find potential for instantiate DOM DocumentFactory builders
    //javax.xml.parsers.DocumentBuilderFactory#newInstance method will be in top of the list
    extension.findBuildMethods(classpath.findClass("javax.xml.parsers.DocumentBuilderFactory")).toList()
```

<!--- MODULE jacodb-core -->
<!--- INDEX org.jacodb.core -->

[Usages]: https://jacodb.org/docs/jacodb-core/org.jacodb.impl.features/-usages/index.html
[Builders]: https://jacodb.org/docs/jacodb-core/org.jacodb.impl.features/-builders/index.html
[InMemoryHierarchy]: https://jacodb.org/docs/jacodb-core/org.jacodb.impl.features/-in-memory-hierarchy/index.html