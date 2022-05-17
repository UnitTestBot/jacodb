package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.api.ClasspathSet
import com.huawei.java.compilation.database.api.CompilationDatabase
import com.huawei.java.compilation.database.impl.tree.ClassTree
import kotlinx.collections.immutable.toPersistentList
import java.io.File

class CompilationDatabaseImpl : CompilationDatabase {

    private val classTree = ClassTree()

    override suspend fun classpathSet(locations: List<File>): ClasspathSet {
        load(locations)
        return ClasspathSetImpl(locations.map { ByteCodeLocationImpl(it) }.toPersistentList(), classTree)
    }

    override suspend fun load(dirOrJar: File) = with(this) {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override suspend fun refresh(): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override fun watchFileSystemChanges(): CompilationDatabase {
        TODO("Not yet implemented")
    }

}