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

import org.objectweb.asm.TypeReference
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.FieldInfo
import org.jacodb.impl.types.TypeNameImpl
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JcFieldImpl(
    override val enclosingClass: JcClassOrInterface,
    private val info: FieldInfo
) : JcField {

    override val name: String
        get() = info.name

    override val declaration = JcDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    override val access: Int
        get() = info.access

    override val type = TypeNameImpl(info.type)

    override val signature: String?
        get() = info.signature

    override val annotations by lazy(PUBLICATION) {
        info.annotations
            .filter { it.typeRef == null } // Type annotations are stored with fields in bytecode, but they are not a part of field in language
            .map { JcAnnotationImpl(it, enclosingClass.classpath) }
    }

    internal val typeAnnotationInfos: List<AnnotationInfo>
        get() = info.annotations.filter { it.typeRef != null && TypeReference(it.typeRef).sort == TypeReference.FIELD }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcFieldImpl) {
            return false
        }
        return other.name == name && other.enclosingClass == enclosingClass
    }

    override fun hashCode(): Int {
        return 31 * enclosingClass.hashCode() + name.hashCode()
    }

    override fun toString(): String {
        return "$enclosingClass#$name"
    }
}