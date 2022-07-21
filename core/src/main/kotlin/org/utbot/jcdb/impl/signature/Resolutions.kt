package org.utbot.jcdb.impl.signature

import org.utbot.jcdb.api.FieldResolution
import org.utbot.jcdb.api.MethodResolution
import org.utbot.jcdb.api.RecordComponentResolution
import org.utbot.jcdb.api.TypeResolution

class FieldResolutionImpl(val fieldType: GenericType) : FieldResolution

class RecordComponentResolutionImpl(val recordComponentType: GenericType) : RecordComponentResolution

class MethodResolutionImpl(
    val returnType: GenericType,
    val parameterTypes: List<GenericType>,
    val exceptionTypes: List<GenericType>,
    val typeVariables: List<FormalTypeVariable>
) : MethodResolution

class TypeResolutionImpl(
    val superClass: GenericType,
    val interfaceType: List<GenericType>,
    val typeVariable: List<FormalTypeVariable>
) : TypeResolution

