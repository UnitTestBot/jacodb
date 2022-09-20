package org.utbot.jcdb.impl.signature

import org.utbot.jcdb.api.FieldResolution
import org.utbot.jcdb.api.MethodResolution
import org.utbot.jcdb.api.RecordComponentResolution
import org.utbot.jcdb.api.TypeResolution

internal class FieldResolutionImpl(val fieldType: SType) : FieldResolution

internal class RecordComponentResolutionImpl(val recordComponentType: SType) : RecordComponentResolution

internal class MethodResolutionImpl(
    val returnType: SType,
    val parameterTypes: List<SType>,
    val exceptionTypes: List<SClassRefType>,
    val typeVariables: List<FormalTypeVariable>
) : MethodResolution

internal class TypeResolutionImpl(
    val superClass: SType,
    val interfaceType: List<SType>,
    val typeVariable: List<FormalTypeVariable>
) : TypeResolution

