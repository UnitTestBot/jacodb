package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.api.ClasspathSet
import com.huawei.java.compilation.database.api.CompilationDatabase
import java.io.File

class CompilationDatabaseImpl: CompilationDatabase {

    override suspend fun classpathSet(locations: List<File>): ClasspathSet {
        TODO("Not yet implemented")
    }

    override suspend fun load(dirOrJar: File): CompilationDatabase {
        TODO("Not yet implemented")
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