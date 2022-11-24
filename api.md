## Settings

JCDBSettings is used for creating instance of `JCDB` instance.

#### `useJavaRuntime`

Use custom (not current process) java runtime for bytecode analysis. 

There are two shortcuts `JCDBSettings#useProcessJavaRuntime` for using current process runtime (default option) and `JCDBSettings#useJavaHomeRuntime` for using Java runtime from JAVA_HOME environment variable  


| parameter | type | description                           |
|-----------|------|---------------------------------------|
| runtime   | File | Required. File points to java runtime |


#### `persistent` 

Specify storing data properties

| parameter    | type     | description                                                                                                  |
|--------------|----------|--------------------------------------------------------------------------------------------------------------|
| location     | string   | Optional. Location on file system to store Sqlite database file. Sqlite will be in-memory if null specified |
| clearOnStart | boolean  | Force Sqlite database to cleanup stored data on startup. false by default                                    |


#### `loadByteCode`

Which files to load on startup

| parameter | type            | description                             |
|-----------|-----------------|-----------------------------------------|
| files     | list of `File`  | jars or folders with compiled java code |


#### `watchFileSystem`

Should database look for filesystem changes

| parameter | type    | description                                                         |
|-----------|---------|---------------------------------------------------------------------|
| delay     | integer | Optional. 10_000 by default. Watch interval for file system changes |

#### `installFeatures` 

Which features jcdb will use 

| parameter | type                | description                                                          |
|-----------|---------------------|----------------------------------------------------------------------|
| features  | Array of JcFeatures | List of features. Features cannot be installed after jcdb is created |


## Database

`JCDB` instance created based on settings. If instance is not needed yet then `close` method should be called.

#### Properties

| property       | type                         | description                                                    |
|----------------|------------------------------|----------------------------------------------------------------|
| locations      | List of `JcByteCodeLocation` | List of locations processed by database                        |
| persistence    | `JCDBPersistence`            | persistence which brings ability to read/write to database     |
| runtimeVersion | `JavaRuntimeVersion`         | version of java runtime which is used for this `jcdb` instance |


#### `classpath`

Creates classpath instances

| parameter  | type            | description                                                  |
|------------|-----------------|--------------------------------------------------------------|
| dirOrJars  | List of `File` | List of files with bytecode to be loaded in current instance |


#### `refresh`

Refreshes state of `jcdb` instance and state of file systen. Should be called to cleanup jars/folders that loaded versions are out of data and which are not used by any classpaths created by `jcdb` instance

#### `rebuildFeatures`

Rebuild indexes for features installed in `jcdb`

#### `awaitBackgroundJobs`

Await background jobs

#### `close`

Is used to clean up resources used by `jcdb` instance 

## Classpath

## Classes

## Types

## Usages (aka call graph)

## Builders

