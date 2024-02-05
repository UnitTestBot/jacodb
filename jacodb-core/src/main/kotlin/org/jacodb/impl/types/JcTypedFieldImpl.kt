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

package org.jacodb.impl.types

import org.jacodb.api.JcField
import org.jacodb.api.JcRefType
import org.jacodb.api.JcSubstitutor
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.ext.isNullable
import org.jacodb.api.throwClassNotFound
import org.jacodb.impl.bytecode.JcAnnotationImpl
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.types.signature.FieldResolutionImpl
import org.jacodb.impl.types.signature.FieldSignature
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JcTypedFieldImpl(
    override val enclosingType: JcRefType,
    override val field: JcField,
    private val substitutor: JcSubstitutor,
) : JcTypedField {

    override val access: Int
        get() = this.field.access

    private val classpath = field.enclosingClass.classpath
    private val resolvedType by lazy(PUBLICATION) {
        val resolution = FieldSignature.of(field) as? FieldResolutionImpl
        resolution?.fieldType
    }

    override val name: String get() = this.field.name

    override val fieldType: JcType by lazy {
        val typeName = field.type.typeName
        val type = resolvedType?.let {
            classpath.typeOf(substitutor.substitute(it))
        } ?: classpath.findTypeOrNull(field.type.typeName)?.copyWithAnnotations(
            (field as? JcFieldImpl)?.typeAnnotationInfos?.map { JcAnnotationImpl(it, field.enclosingClass.classpath) }
                ?: listOf()
        ) ?: typeName.throwClassNotFound()

        field.isNullable?.let {
            (type as? JcRefType)?.copyWithNullability(it)
        } ?: type
    }

}
