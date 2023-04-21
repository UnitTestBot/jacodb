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

package org.jacodb.impl.features.classpaths

import org.jacodb.api.JcByteCodeLocation
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathExtFeature
import org.jacodb.api.RegisteredLocation
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClass
import org.jacodb.impl.features.classpaths.virtual.VirtualClassesBuilder
import java.util.Optional

open class VirtualClasses(
    val classes: List<JcVirtualClass>,
    private val virtualLocation: VirtualLocation = VirtualLocation()
) : JcClasspathExtFeature {

    companion object {

        @JvmStatic
        fun builder(factory: VirtualClassesBuilder.() -> Unit): VirtualClasses {
            return VirtualClassesBuilder().also { it.factory() }.build()
        }

        @JvmStatic
        fun builder(): VirtualClassesBuilder {
            return VirtualClassesBuilder()
        }

    }

    private val map = classes.associateBy { it.name }

    override fun tryFindClass(classpath: JcClasspath, name: String): Optional<JcClassOrInterface>? {
        val clazz = map[name]
        if(clazz != null){
            clazz.bind(classpath, virtualLocation)
            return Optional.of(clazz)
        }
        return null
    }

}

class VirtualLocation : RegisteredLocation {
    override val jcLocation: JcByteCodeLocation?
        get() = null

    override val id: Long
        get() = -1

    override val path: String = "/dev/null"

    override val isRuntime: Boolean
        get() = false

}