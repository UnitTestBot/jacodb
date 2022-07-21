package org.utbot.jcdb.api

interface Hook {

    fun afterStart()

    fun afterStop() {}
}