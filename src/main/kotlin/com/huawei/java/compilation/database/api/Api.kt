package com.huawei.java.compilation.database.api

import java.io.File

interface DatabaseClass {

    val location: CodeLocation

    val name: String
    val simpleName: String

    val methods: List<DatabaseClassMethod>

    val parents: List<DatabaseClass>
    val interfaces: List<DatabaseClass>
    val annotations: List<DatabaseClass>
}

interface CodeLocation {

    var isJar: Boolean

    val file: File
    val hash: String
}

interface DatabaseClassMethod {

    val name: String
    val signature: Any
    val annotations: List<DatabaseClass>

    suspend fun readBody(): Any //TODO return something
}


interface CompilationDatabase {

    suspend fun findClass(location: CodeLocation, name: String): DatabaseClass?
    suspend fun findClass(name: String): DatabaseClass?

    suspend fun load(dirOrJar: File)
    suspend fun load(dirOrJars: List<File>)

    suspend fun refresh()

    fun watchFileSystemChanges()
}

object CompilationDatabaseFactory {

    suspend fun newInMemoryDatabase(vararg locations: File): CompilationDatabase {
        TODO("implement me")
    }

    suspend fun newPersistedDatabase(vararg locations: File): CompilationDatabase {
        TODO("implement me")
    }

}