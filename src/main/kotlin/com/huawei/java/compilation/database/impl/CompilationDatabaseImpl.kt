package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.ApiLevel
import com.huawei.java.compilation.database.api.ByteCodeLocation
import com.huawei.java.compilation.database.api.ClasspathSet
import com.huawei.java.compilation.database.api.CompilationDatabase
import com.huawei.java.compilation.database.impl.fs.JavaRuntime
import com.huawei.java.compilation.database.impl.fs.asByteCodeLocation
import com.huawei.java.compilation.database.impl.fs.filterExisted
import com.huawei.java.compilation.database.impl.fs.sources
import com.huawei.java.compilation.database.impl.tree.ClassTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KLogging
import java.io.File

class CompilationDatabaseImpl(private val apiLevel: ApiLevel, javaLocation: File) : CompilationDatabase {
    companion object : KLogging()

    private val classTree = ClassTree()
    private val javaRuntime = JavaRuntime(apiLevel, javaLocation)

    suspend fun loadJavaLibraries() {
        javaRuntime.allLocations.loadAll()
    }

    override suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet {
        val existedLocations = dirOrJars.filterExisted()
        load(existedLocations)
        return ClasspathSetImpl(existedLocations.map { it.asByteCodeLocation(apiLevel) }.toList() + javaRuntime.allLocations, classTree)
    }

    override suspend fun load(dirOrJar: File) = apply {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        dirOrJars.filterExisted().map { it.asByteCodeLocation(apiLevel) }.loadAll()
    }

    private suspend fun List<ByteCodeLocation>.loadAll() = this@CompilationDatabaseImpl.apply {
        withContext(Dispatchers.IO) {
            map { location ->
                launch(Dispatchers.IO) {
                    location.sources().forEach {
                        classTree.addClass(it)
                    }
                }
            }
        }.joinAll()
    }

    override suspend fun refresh(): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override fun watchFileSystemChanges(): CompilationDatabase {
        TODO("Not yet implemented")
    }

}