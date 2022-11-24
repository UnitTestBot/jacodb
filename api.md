## Settings

JCDBSettings is used for creating instance of `JCDB` instance.

`JCDBSettings#useJavaRuntime`- use custom (not current process) java runtime for bytecode analysis.
There are two shortcuts `JCDBSettings#useProcessJavaRuntime` for using current process runtime (default option) and `JCDBSettings#useJavaHomeRuntime` for using Java runtime from JAVA_HOME environment variable  


| parameter | type | description                           |
|-----------|------|---------------------------------------|
| runtime   | File | Required. File points to java runtime |


`JCDBSettings#persistent` - specify storing data properties

| parameter    | type     | description                                                                                                  |
|--------------|----------|--------------------------------------------------------------------------------------------------------------|
| location     | string   | Optional. Location on file system to store Sqlite database file. Sqlite will be in-memory if null specified |
| clearOnStart | boolean  | Force Sqlite database to cleanup stored data on startup. false by default                                    |


`JCDBSettings#loadByteCode`- which files to load on startup

| parameter | type            | description                             |
|-----------|-----------------|-----------------------------------------|
| files     | list of `File`  | jars or folders with compiled java code |


`JCDBSettings#watchFileSystem`- should database look for filesystem changes

| parameter | type    | description                                                         |
|-----------|---------|---------------------------------------------------------------------|
| delay     | integer | Optional. 10_000 by default. Watch interval for file system changes |

`JCDBSettings#installFeatures`- which features jcdb will use 

| parameter | type                | description                                                          |
|-----------|---------------------|----------------------------------------------------------------------|
| features  | Array of JcFeatures | List of features. Features cannot be installed after jcdb is created |


## Database




## Classpath

## Classes

## Types

## Usages (aka call graph)

## Builders

