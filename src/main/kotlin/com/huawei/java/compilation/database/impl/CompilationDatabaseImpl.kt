package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.api.ClasspathSet
import com.huawei.java.compilation.database.api.CompilationDatabase
import com.huawei.java.compilation.database.impl.reader.readClasses
import com.huawei.java.compilation.database.impl.tree.ClassTree
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CompilationDatabaseImpl : CompilationDatabase {

    private val classTree = ClassTree()

    override suspend fun classpathSet(locations: List<File>): ClasspathSet {
        load(locations)
        return ClasspathSetImpl(locations.map { it.byteCodeLocation }.toPersistentList(), classTree)
    }

    override suspend fun load(dirOrJar: File) = apply {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        dirOrJars.forEach {
            if (!it.exists()) {
                throw IllegalStateException("file or folder does not exists: ${it.absolutePath}")
            }
        }
        withContext(Dispatchers.IO) {
            dirOrJars.map { dirOrJar ->
                launch(Dispatchers.IO) {
                    val location = dirOrJar.byteCodeLocation
                    location.readClasses().forEach {
                        classTree.addClass(location, it.name, it)
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