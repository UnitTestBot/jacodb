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

package org.jacodb.testing

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.methods
import org.jacodb.impl.fs.asClassInfo
import org.jacodb.impl.types.ParameterInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ParameterNamesTest : BaseTest() {
    companion object : WithDB()

    private val target = Files.createTempDirectory("jcdb-temp")

    @Test
    fun checkParameterName() {
        val clazz = cp.findClass("GenericsApi")
        runBlocking {
            cp.db.load(target.toFile())
        }
        val method = clazz.methods.firstOrNull { jcMethod -> jcMethod.name == "call" }
        Assertions.assertNotNull(method)
        Assertions.assertNull(method?.parameters?.get(0)?.name)
        Assertions.assertEquals("arg", method?.parameterNames?.get(0))
    }

    private val JcMethod.parameterNames: List<String?>
        get() {
            return enclosingClass.asmNode()
                .asClassInfo(enclosingClass.bytecode()).methods.find { info -> info.name == name && info.desc == description }
                ?.parametersInfo?.map(ParameterInfo::name)
                ?: parameters.map(JcParameter::name)
        }
}
