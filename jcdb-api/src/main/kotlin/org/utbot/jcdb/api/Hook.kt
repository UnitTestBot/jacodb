package org.utbot.jcdb.api

interface Hook {

    suspend fun afterStart()

    fun afterStop() {}
}