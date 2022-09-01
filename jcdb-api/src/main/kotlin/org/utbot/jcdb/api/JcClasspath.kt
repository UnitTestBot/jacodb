package org.utbot.jcdb.api

interface JcClasspath {

    suspend fun findOrNull(name: String): JcClassOrInterface

    suspend fun typeOf(jcClass: JcClassOrInterface): JcRefType

    suspend fun arrayTypeOf(elementType: JcType): JcArrayType

    val locations: List<JcBytecodeLocation>

    suspend fun refreshed(): JcClasspath
}