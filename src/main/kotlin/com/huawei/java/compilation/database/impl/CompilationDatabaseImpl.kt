package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.ApiLevel
import com.huawei.java.compilation.database.api.ByteCodeLocation
import com.huawei.java.compilation.database.api.ClasspathSet
import com.huawei.java.compilation.database.api.CompilationDatabase
import com.huawei.java.compilation.database.impl.fs.JavaRuntime
import com.huawei.java.compilation.database.impl.fs.asByteCodeLocation
import com.huawei.java.compilation.database.impl.fs.filterExisted
import com.huawei.java.compilation.database.impl.tree.ClassTree
import com.huawei.java.compilation.database.impl.tree.SubTypesInstallationListener
import kotlinx.coroutines.*
import mu.KLogging
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object BackgroundScope : CoroutineScope {
    override val coroutineContext = Dispatchers.IO + SupervisorJob()
}

class CompilationDatabaseImpl(private val apiLevel: ApiLevel, javaLocation: File) : CompilationDatabase {
    companion object : KLogging()

    private val classTree = ClassTree(listeners = listOf(SubTypesInstallationListener))
    private val javaRuntime = JavaRuntime(apiLevel, javaLocation)

    private val backgroundJobs: Queue<Job> = ConcurrentLinkedQueue()

    suspend fun loadJavaLibraries() {
        javaRuntime.allLocations.loadAll()
    }

    override suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet {
        val existedLocations = dirOrJars.filterExisted().map { it.asByteCodeLocation(apiLevel) }.also {
            it.loadAll()
        }
        return ClasspathSetImpl(existedLocations.toList() + javaRuntime.allLocations, this, classTree)
    }

    override suspend fun load(dirOrJar: File) = apply {
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        dirOrJars.filterExisted().map { it.asByteCodeLocation(apiLevel) }.loadAll()
    }

    private suspend fun List<ByteCodeLocation>.loadAll() = apply {
        val actions = ConcurrentLinkedQueue<suspend () -> Unit>()

        withContext(Dispatchers.IO) {
            map { location ->
                async {
                    val asyncJob = location.loader().load(classTree)
                    actions.add(asyncJob)
                }
            }
        }.joinAll()
        backgroundJobs.add(BackgroundScope.launch {
            actions.map { action ->
                async {
                    action()
                }
            }.joinAll()
        })
    }

    override suspend fun refresh(): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override fun watchFileSystemChanges(): CompilationDatabase {
        TODO("Not yet implemented")
    }

    suspend fun awaitBackgroundJobs() {
        backgroundJobs.toList().joinAll()
    }
}