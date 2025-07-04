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

package org.jacodb.ets.proto

import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsEnumValueType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsLiteralType
import org.jacodb.ets.model.EtsNeverType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsRawType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsVoidType
import model.AliasType as ProtoAliasType
import model.AnyType as ProtoAnyType
import model.ArrayType as ProtoArrayType
import model.BooleanType as ProtoBooleanType
import model.ClassType as ProtoClassType
import model.FunctionType as ProtoFunctionType
import model.GenericType as ProtoGenericType
import model.IntersectionType as ProtoIntersectionType
import model.LiteralType as ProtoLiteralType
import model.NeverType as ProtoNeverType
import model.NullType as ProtoNullType
import model.NumberType as ProtoNumberType
import model.StringType as ProtoStringType
import model.TupleType as ProtoTupleType
import model.Type as ProtoType
import model.UnclearRefType as ProtoUnclearRefType
import model.UndefinedType as ProtoUndefinedType
import model.UnionType as ProtoUnionType
import model.UnknownType as ProtoUnknownType
import model.VoidType as ProtoVoidType

fun EtsType.toProto(): ProtoType = accept(EtsTypeToProto)

internal object EtsTypeToProto : EtsType.Visitor<ProtoType> {
    override fun visit(type: EtsRawType): ProtoType {
        // NOTE: !!!
        val unknownType = ProtoUnknownType()
        return ProtoType(unknown_type = unknownType)
    }

    override fun visit(type: EtsAnyType): ProtoType {
        val anyType = ProtoAnyType()
        return ProtoType(any_type = anyType)
    }

    override fun visit(type: EtsUnknownType): ProtoType {
        val unknownType = ProtoUnknownType()
        return ProtoType(unknown_type = unknownType)
    }

    override fun visit(type: EtsUnionType): ProtoType {
        val unionType = ProtoUnionType(
            types = type.types.map { it.toProto() },
        )
        return ProtoType(union_type = unionType)
    }

    override fun visit(type: EtsIntersectionType): ProtoType {
        val intersectionType = ProtoIntersectionType(
            types = type.types.map { it.toProto() },
        )
        return ProtoType(intersection_type = intersectionType)
    }

    override fun visit(type: EtsGenericType): ProtoType {
        val genericType = ProtoGenericType(
            type_name = type.typeName,
            default_type = type.defaultType?.toProto(),
            constraint = type.constraint?.toProto()
        )
        return ProtoType(generic_type = genericType)
    }

    override fun visit(type: EtsAliasType): ProtoType {
        val aliasType = ProtoAliasType(
            name = type.name,
            original_type = type.originalType.toProto(),
            // TODO: local signature
        )
        return ProtoType(alias_type = aliasType)
    }

    override fun visit(type: EtsEnumValueType): ProtoType {
        TODO("${type::class.java.simpleName} is not supported yet")
    }

    override fun visit(type: EtsBooleanType): ProtoType {
        val booleanType = ProtoBooleanType()
        return ProtoType(boolean_type = booleanType)
    }

    override fun visit(type: EtsNumberType): ProtoType {
        val numberType = ProtoNumberType()
        return ProtoType(number_type = numberType)
    }

    override fun visit(type: EtsStringType): ProtoType {
        val stringType = ProtoStringType()
        return ProtoType(string_type = stringType)
    }

    override fun visit(type: EtsNullType): ProtoType {
        val nullType = ProtoNullType()
        return ProtoType(null_type = nullType)
    }

    override fun visit(type: EtsUndefinedType): ProtoType {
        val undefinedType = ProtoUndefinedType()
        return ProtoType(undefined_type = undefinedType)
    }

    override fun visit(type: EtsVoidType): ProtoType {
        val voidType = ProtoVoidType()
        return ProtoType(void_type = voidType)
    }

    override fun visit(type: EtsNeverType): ProtoType {
        val neverType = ProtoNeverType()
        return ProtoType(never_type = neverType)
    }

    override fun visit(type: EtsLiteralType): ProtoType {
        val literalType = ProtoLiteralType(
            literal_name = type.literalTypeName,
        )
        return ProtoType(literal_type = literalType)
    }

    override fun visit(type: EtsClassType): ProtoType {
        val classType = ProtoClassType(
            signature = type.signature.toProto(),
            type_parameters = type.typeParameters.map { it.toProto() },
        )
        return ProtoType(class_type = classType)
    }

    override fun visit(type: EtsUnclearRefType): ProtoType {
        val unclearRefType = ProtoUnclearRefType(
            name = type.typeName,
            type_parameters = type.typeParameters.map { it.toProto() },
        )
        return ProtoType(unclear_ref_type = unclearRefType)
    }

    override fun visit(type: EtsArrayType): ProtoType {
        val arrayType = ProtoArrayType(
            element_type = type.elementType.toProto(),
            dimensions = type.dimensions,
        )
        return ProtoType(array_type = arrayType)
    }

    override fun visit(type: EtsTupleType): ProtoType {
        val tupleType = ProtoTupleType(
            types = type.types.map { it.toProto() },
        )
        return ProtoType(tuple_type = tupleType)
    }

    override fun visit(type: EtsFunctionType): ProtoType {
        val functionType = ProtoFunctionType(
            signature = type.signature.toProto(),
            type_parameters = type.typeParameters.map { it.toProto() },
        )
        return ProtoType(function_type = functionType)
    }
}
