package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.ApiLevel
import com.huawei.java.compilation.database.api.ClasspathSet
import com.huawei.java.compilation.database.api.CompilationDatabase
import com.huawei.java.compilation.database.impl.fs.asByteCodeLocation
import com.huawei.java.compilation.database.impl.fs.filterExisted
import com.huawei.java.compilation.database.impl.fs.sources
import com.huawei.java.compilation.database.impl.tree.ClassTree
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KLogging
import java.io.File

class CompilationDatabaseImpl(private val apiLevel: ApiLevel) : CompilationDatabase {

    companion object : KLogging()

    private val classTree = ClassTree()

    override suspend fun classpathSet(locations: List<File>): ClasspathSet {
        val existedLocations = locations.filterExisted()
        load(existedLocations)
        return ClasspathSetImpl(existedLocations.map { it.asByteCodeLocation(apiLevel) }.toPersistentList(), classTree)
    }

    override suspend fun load(dirOrJar: File) = apply {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        val validSources = dirOrJars.filterExisted()
        withContext(Dispatchers.IO) {
            validSources.map { dirOrJar ->
                launch(Dispatchers.IO) {
                    val location = dirOrJar.asByteCodeLocation(apiLevel)
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