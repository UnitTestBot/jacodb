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

package org.jacodb.ets.dto

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

fun EtsValue.toDto(): ValueDto = accept(EtsValueToDto)

fun EtsLocal.toDto(): LocalDto = LocalDto(
    name = name,
    type = type.toDto(),
)

fun EtsConstant.toDto(): ConstantDto = ConstantDto(
    value = toString(),
    type = type.toDto(),
)

private object EtsValueToDto : EtsValue.Visitor<ValueDto> {
    override fun visit(value: EtsLocal): LocalDto {
        return value.toDto()
    }

    override fun visit(value: EtsConstant): ValueDto {
        return value.toDto()
    }

    override fun visit(value: EtsStringConstant): ValueDto {
        return value.toDto()
    }

    override fun visit(value: EtsBooleanConstant): ValueDto {
        return value.toDto()
    }

    override fun visit(value: EtsNumberConstant): ValueDto {
        return value.toDto()
    }

    override fun visit(value: EtsNullConstant): ValueDto {
        return value.toDto()
    }

    override fun visit(value: EtsUndefinedConstant): ValueDto {
        return value.toDto()
    }

    override fun visit(value: EtsThis): ValueDto {
        return ThisRefDto(
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsParameterRef): ValueDto {
        return ParameterRefDto(
            index = value.index,
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsArrayAccess): ValueDto {
        return ArrayRefDto(
            array = value.array.toDto(),
            index = value.index.toDto(),
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsInstanceFieldRef): ValueDto {
        return InstanceFieldRefDto(
            instance = value.instance.toDto(),
            field = value.field.toDto(),
        )
    }

    override fun visit(value: EtsStaticFieldRef): ValueDto {
        return StaticFieldRefDto(
            field = value.field.toDto(),
        )
    }
}
