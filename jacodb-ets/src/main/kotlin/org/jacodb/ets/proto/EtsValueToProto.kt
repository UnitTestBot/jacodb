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

@file:Suppress("LocalVariableName")

package org.jacodb.ets.proto

import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsConstant
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsValue
import model.ArrayAccess as ProtoArrayAccess
import model.Constant as ProtoConstant
import model.FieldRef as ProtoFieldRef
import model.InstanceFieldRef as ProtoInstanceFieldRef
import model.Local as ProtoLocal
import model.ParameterRef as ProtoParameterRef
import model.Ref as ProtoRef
import model.StaticFieldRef as ProtoStaticFieldRef
import model.This as ProtoThis
import model.Value as ProtoValue

fun EtsValue.toProto(): ProtoValue = accept(EtsValueToProto)

internal object EtsValueToProto : EtsValue.Visitor<ProtoValue> {
    override fun visit(value: EtsLocal): ProtoValue {
        val local = value.toProto()
        return ProtoValue(local = local)
    }

    private fun visitConstant(value: EtsConstant): ProtoValue {
        val constant = value.toProto()
        return ProtoValue(constant = constant)
    }

    override fun visit(value: EtsConstant): ProtoValue {
        return visitConstant(value)
    }

    override fun visit(value: EtsStringConstant): ProtoValue {
        return visitConstant(value)
    }

    override fun visit(value: EtsBooleanConstant): ProtoValue {
        return visitConstant(value)
    }

    override fun visit(value: EtsNumberConstant): ProtoValue {
        return visitConstant(value)
    }

    override fun visit(value: EtsNullConstant): ProtoValue {
        return visitConstant(value)
    }

    override fun visit(value: EtsUndefinedConstant): ProtoValue {
        return visitConstant(value)
    }

    override fun visit(value: EtsThis): ProtoValue {
        val this_ = ProtoThis(type = value.type.toProto())
        return ProtoValue(ref = ProtoRef(this_ = this_))
    }

    override fun visit(value: EtsParameterRef): ProtoValue {
        val paramRef = ProtoParameterRef(
            index = value.index,
            type = value.type.toProto(),
        )
        return ProtoValue(ref = ProtoRef(parameter = paramRef))
    }

    override fun visit(value: EtsArrayAccess): ProtoValue {
        val arrayAccess = ProtoArrayAccess(
            array = value.array.toProto(),
            index = value.index.toProto(),
            type = value.type.toProto(),
        )
        return ProtoValue(ref = ProtoRef(array_access = arrayAccess))
    }

    override fun visit(value: EtsInstanceFieldRef): ProtoValue {
        val instanceFieldRef = ProtoInstanceFieldRef(
            instance = value.instance.toProto(),
            field_ = value.field.toProto(),
            type = value.type.toProto(),
        )
        return ProtoValue(ref = ProtoRef(field_ref = ProtoFieldRef(instance = instanceFieldRef)))
    }

    override fun visit(value: EtsStaticFieldRef): ProtoValue {
        val staticFieldRef = ProtoStaticFieldRef(
            field_ = value.field.toProto(),
            type = value.type.toProto(),
        )
        return ProtoValue(ref = ProtoRef(field_ref = ProtoFieldRef(static = staticFieldRef)))
    }
}

fun EtsLocal.toProto(): ProtoLocal = ProtoLocal(
    name = this.name,
    type = this.type.toProto(),
)

fun EtsConstant.toProto(): ProtoConstant = ProtoConstant(
    value_ = this.toString(),
    type = this.type.toProto()
)
