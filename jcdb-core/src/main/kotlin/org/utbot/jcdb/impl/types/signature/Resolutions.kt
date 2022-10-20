package org.utbot.jcdb.impl.types.signature

import org.utbot.jcdb.api.FieldResolution
import org.utbot.jcdb.api.MethodResolution
import org.utbot.jcdb.api.RecordComponentResolution
import org.utbot.jcdb.api.TypeResolution

internal class FieldResolutionImpl(val fieldType: JvmType) : FieldResolution

internal class RecordComponentResolutionImpl(val recordComponentType: JvmType) : RecordComponentResolution

internal class MethodResolutionImpl(
    val returnType: JvmType,
    val parameterTypes: List<JvmType>,
    val exceptionTypes: List<JvmClassRefType>,
    val typeVariables: List<JvmTypeParameterDeclaration>
) : MethodResolution

internal class TypeResolutionImpl(
    val superClass: JvmType,
    val interfaceType: List<JvmType>,
    val typeVariables: List<JvmTypeParameterDeclaration>
) : TypeResolution

