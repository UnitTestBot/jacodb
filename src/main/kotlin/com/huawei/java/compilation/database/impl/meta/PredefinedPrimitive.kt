package com.huawei.java.compilation.database.impl.meta

import com.huawei.java.compilation.database.api.ClassId
import com.huawei.java.compilation.database.api.FieldId
import com.huawei.java.compilation.database.api.MethodId
import kotlinx.collections.immutable.persistentListOf
import org.objectweb.asm.Opcodes

class PredefinedPrimitive(override val simpleName: String) : ClassId {

    companion object {
        val values = persistentListOf(
            PredefinedPrimitive("boolean"),
            PredefinedPrimitive("char"),
            PredefinedPrimitive("short"),
            PredefinedPrimitive("int"),
            PredefinedPrimitive("long"),
            PredefinedPrimitive("float"),
            PredefinedPrimitive("double")
        )
    }


    override val name: String get() = simpleName
    override val location = null

    override suspend fun methods() = emptyList<MethodId>()

    override suspend fun superclass() = null

    override suspend fun interfaces() = emptyList<ClassId>()

    override suspend fun annotations() = emptyList<ClassId>()

    override suspend fun fields() = emptyList<FieldId>()

    override suspend fun access() = Opcodes.ACC_PUBLIC
}