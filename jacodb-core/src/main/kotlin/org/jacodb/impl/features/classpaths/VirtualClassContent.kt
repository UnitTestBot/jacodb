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

import org.jacodb.api.JcClassExtFeature
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcField
import org.jacodb.api.JcInstExtFeature
import org.jacodb.api.JcMethod
import org.jacodb.impl.features.classpaths.virtual.VirtualClassesBuilder

class VirtualClassContent(private val builders: List<VirtualClassContentBuilder>) : JcClassExtFeature,
    JcInstExtFeature {

    companion object {

        @JvmStatic
        fun builder(factory: VirtualClassContentsBuilder.() -> Unit): VirtualClassContent {
            return VirtualClassContentsBuilder().also { it.factory() }.build()
        }

        @JvmStatic
        fun builder(): VirtualClassContentsBuilder {
            return VirtualClassContentsBuilder()
        }

    }

    override fun fieldsOf(clazz: JcClassOrInterface): List<JcField>? {
        if (builders.isNotEmpty()) {
            builders.forEach {
                if (it.matcher(clazz)) {
                    return it.fieldConfigs.map { config ->
                        val builder = VirtualClassesBuilder.VirtualFieldBuilder()
                        config(builder, clazz)
                        builder.build().also { field ->
                            field.bind(clazz)
                        }
                    }
                }
            }
        }
        return null
    }

    override fun methodsOf(clazz: JcClassOrInterface): List<JcMethod>? {
        if (builders.isNotEmpty()) {
            builders.forEach {
                if (it.matcher(clazz)) {
                    return it.methodConfigs.map { config ->
                        val builder = VirtualClassesBuilder.VirtualMethodBuilder()
                        config(builder, clazz)
                        builder.build().also { method ->
                            method.bind(clazz)
                        }
                    }
                }
            }
        }
        return null

    }

}

class VirtualClassContentsBuilder() {
    internal val builders = ArrayList<VirtualClassContentBuilder>()

    fun content(builder: VirtualClassContentBuilder.() -> Unit) = apply {
        VirtualClassContentBuilder().also {
            it.builder()
            builders.add(it)
        }
    }


    fun build() = VirtualClassContent(builders)

}


class VirtualClassContentBuilder {
    internal var matcher: (JcClassOrInterface) -> Boolean = { false }
    internal val fieldConfigs = ArrayList<(VirtualClassesBuilder.VirtualFieldBuilder, JcClassOrInterface) -> Unit>()
    internal val methodConfigs = ArrayList<(VirtualClassesBuilder.VirtualMethodBuilder, JcClassOrInterface) -> Unit>()

    fun matcher(m: (JcClassOrInterface) -> Boolean) = apply {
        matcher = m
    }

    fun field(configure: (VirtualClassesBuilder.VirtualFieldBuilder, JcClassOrInterface) -> Unit) = apply {
        fieldConfigs.add(configure)
    }

    fun method(configure: (VirtualClassesBuilder.VirtualMethodBuilder, JcClassOrInterface) -> Unit) = apply {
        methodConfigs.add(configure)
    }

}