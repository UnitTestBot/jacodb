/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.panda.dynamic.api

import org.jacodb.api.common.Project

class PandaProject(
    val classes: List<PandaClass>,
) : Project {

    private val std = PandaStdLib

    init {
        classes.forEach { clazz ->
            clazz.methods.forEach { method ->
                method.project = this
            }
            clazz.project = this
        }
    }

    override fun findTypeOrNull(name: String): PandaType? {
        return null
    }

    fun getGlobalClass(): PandaClass {
        return findClassOrNull("L_GLOBAL") ?: error("no global class")
    }

//    fun findObject(name: String, currentClassName: String): PandaField {
//        findClassOrNull(currentClassName)?.let { clazz ->
//            return clazz.fields.find { it.name == name } ?: findObject(name, clazz.superClassName)
//        }
//
//        throw IllegalStateException("couldn't find object $name starting from class $currentClassName")
//    }

    fun findInstanceMethodInStd(instanceName: String, methodName: String): PandaStdMethod {
        std.fields.find { it.name == instanceName }?.let { obj ->
            return (obj.methods.find { it.name == methodName }
                ?: error("no method $methodName for $instanceName"))
                .also {
                    it.project = this
                }
        } ?: error("no std field $instanceName")
    }

    /*  TODO: WIP
        Order of search:
            1. Local vars
            2. Global vars
            3. Imports
            4. STD
     */
    fun findMethodByInstanceOrEmpty(instanceName: String, methodName: String, currentClassName: String): PandaMethod {
        if (instanceName == "this") return findMethodOrNull(methodName, currentClassName)
            ?: PandaMethod(methodName, PandaAnyType)
        return findInstanceMethodInStd(instanceName, methodName)
    }

    fun findClassOrNull(name: String): PandaClass? = classes.find { it.name == name }

    fun findMethodOrNull(name: String, currentClassName: String): PandaMethod? =
        findClassOrNull(currentClassName)?.methods?.find { it.name == name }

    override fun close() {}

    companion object {
        fun empty(): PandaProject = PandaProject(emptyList())
    }
}

class PandaObject(
    val name: String,
    val methods: List<PandaStdMethod>,
)

object PandaStdLib {
    val fields = listOf(
        PandaObject(
            "console",
            listOf(PandaStdMethod("log", PandaAnyType))
        )
    )
}
