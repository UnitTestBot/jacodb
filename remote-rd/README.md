## Rd extension for Java Compilation Database

Extension provides proxy implementation to java compilation database running in a separate system process. Proxy implementation uses [rd protocol](https://github.com/JetBrains/rd) under the hood.

### Limitations

Rd protocol implies 1-1 communication between server and client. That means that stopping client database implies stopping server as well.

### Usage example

Create server:

```kotlin
    val db = compilationDatabase {
        useProcessJavaRuntime()
        exposeRd(port = 9090) // this will expose rd based server api
    }
```

creating client: 

```kotlin
    val clientDB: CompilationDatabase = remoteRdClient(port = 9090) // this will create client database
    val classId = clientDB.classpathSet(emptyList()).findClassOrNull("java.util.HashMap")
    println(classId.methods().size)
    clientDB.close() // will close server side extension.
```

