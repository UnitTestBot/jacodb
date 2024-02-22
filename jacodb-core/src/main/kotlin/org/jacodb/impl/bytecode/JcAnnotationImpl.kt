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

package org.jacodb.impl.bytecode

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.enumValues
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.AnnotationValue
import org.jacodb.impl.types.AnnotationValueList
import org.jacodb.impl.types.ClassRef
import org.jacodb.impl.types.EnumRef
import org.jacodb.impl.types.PrimitiveValue
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JcAnnotationImpl(
    private val info: AnnotationInfo,
    private val classpath: JcClasspath
) : JcAnnotation {

    override val jcClass by lazy(PUBLICATION) {
        classpath.findClassOrNull(info.className)
    }

    override val values by lazy(PUBLICATION) {
        val size = info.values.size
        if (size > 0) {
            info.values.associate { it.first to fixValue(it.second) }
        } else {
            emptyMap()
        }
    }

    override val visible: Boolean get() = info.visible
    override val name: String get() = info.className

    override fun matches(className: String): Boolean {
        return info.className == className
    }

    private fun fixValue(value: AnnotationValue): Any? {
        return when (value) {
            is PrimitiveValue -> value.value
            is ClassRef -> classpath.findClassOrNull(value.className)
            is EnumRef -> classpath.findClassOrNull(value.className)?.enumValues
                ?.firstOrNull { it.name == value.enumName }

            is AnnotationInfo -> JcAnnotationImpl(value, classpath)
            is AnnotationValueList -> value.annotations.map { fixValue(it) }
        }
    }

    override fun toString(): String {
        return "@${name}(${values.entries.joinToString { "${it.key}=${it.value})" }})"
    }
}
