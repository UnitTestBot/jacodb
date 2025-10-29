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

import mu.KotlinLogging
import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBitAndExpr
import org.jacodb.ets.model.EtsBitNotExpr
import org.jacodb.ets.model.EtsBitOrExpr
import org.jacodb.ets.model.EtsBitXorExpr
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsBooleanLiteralType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsCaughtExceptionRef
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsConstant
import org.jacodb.ets.model.EtsDecorator
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEnumValueType
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
import org.jacodb.ets.model.EtsExportInfo
import org.jacodb.ets.model.EtsExportType
import org.jacodb.ets.model.EtsExpr
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldRef
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsGlobalRef
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsImportInfo
import org.jacodb.ets.model.EtsImportType
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsLeftShiftExpr
import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsLocalSignature
import org.jacodb.ets.model.EtsLtEqExpr
import org.jacodb.ets.model.EtsLtExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsModifiers
import org.jacodb.ets.model.EtsMulExpr
import org.jacodb.ets.model.EtsNamespace
import org.jacodb.ets.model.EtsNamespaceSignature
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNeverType
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNotEqExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNullishCoalescingExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsNumberLiteralType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPreDecExpr
import org.jacodb.ets.model.EtsPreIncExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRawEntity
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsRawType
import org.jacodb.ets.model.EtsRemExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsRightShiftExpr
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsStrictEqExpr
import org.jacodb.ets.model.EtsStrictNotEqExpr
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsSubExpr
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsTrap
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsTypeOfExpr
import org.jacodb.ets.model.EtsUnaryPlusExpr
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsUnsignedRightShiftExpr
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.model.EtsVoidType
import org.jacodb.ets.model.EtsYieldExpr

private val logger = KotlinLogging.logger {}

class EtsMethodBuilder(
    signature: EtsMethodSignature,
    typeParameters: List<EtsType> = emptyList(),
    modifiers: EtsModifiers = EtsModifiers.EMPTY,
    decorators: List<EtsDecorator> = emptyList(),
    locals: List<EtsLocal> = emptyList(),
) {
    private val locals = locals.toMutableList()

    private val method = EtsMethodImpl(signature, typeParameters, modifiers, decorators).also {
        it.body.locals = this.locals
    }

    private lateinit var currentStmts: MutableList<EtsStmt>

    private var freeTempLocal: Int = 0

    private fun newTempLocal(): EtsLocal {
        val local = EtsLocal("_tmp${freeTempLocal++}")
        this@EtsMethodBuilder.locals += local
        return local
    }

    private fun loc(): EtsStmtLocation {
        return EtsStmtLocation.stub(method)
    }

    private var built: Boolean = false

    fun build(cfgDto: CfgDto, trapsDto: List<TrapDto>): EtsMethod {
        require(!built) { "Method has already been built" }
        val cfg = cfgDto.toEtsCfg()
        method.body.cfg = cfg
        method.body.traps = trapsDto.map { trapDto ->
            EtsTrap(
                tryBlocks = trapDto.tryBlocks.map { i -> cfg.blocks[i] },
                catchBlocks = trapDto.catchBlocks.map { i -> cfg.blocks[i] },
            )
        }
        built = true
        return method
    }

    private fun ensureLocal(entity: EtsEntity): EtsLocal {
        if (entity is EtsLocal) {
            return entity
        }
        val newLocal = newTempLocal()
        currentStmts += EtsAssignStmt(
            location = loc(),
            lhv = newLocal,
            rhv = entity,
        )
        return newLocal
    }

    private fun ensureOneAddress(entity: EtsEntity): EtsValue {
        if (entity is EtsExpr || entity is EtsFieldRef || entity is EtsArrayAccess) {
            return ensureLocal(entity)
        } else {
            check(entity is EtsValue) {
                "Expected EtsValue, but got $entity"
            }
            return entity
        }
    }

    private fun StmtDto.toEtsStmt(): EtsStmt = when (this) {
        is NopStmtDto -> {
            EtsNopStmt(location = loc())
        }

        is AssignStmtDto -> {
            val lhv = left.toEtsEntity() as EtsValue // safe cast
            check(lhv is EtsLocal || lhv is EtsFieldRef || lhv is EtsArrayAccess) {
                "LHV of AssignStmt should be EtsLocal, EtsFieldRef, or EtsArrayAccess, but got $lhv"
            }
            val rhv = right.toEtsEntity().let { rhv ->
                if (lhv is EtsLocal) {
                    if (rhv is EtsCastExpr && rhv.arg is EtsExpr) {
                        EtsCastExpr(ensureLocal(rhv.arg), rhv.type)
                    } else {
                        rhv
                    }
                } else if (rhv is EtsCastExpr || rhv is EtsNewExpr) {
                    rhv
                } else {
                    ensureOneAddress(rhv)
                }
            }
            EtsAssignStmt(
                location = loc(),
                lhv = lhv,
                rhv = rhv,
            )
        }

        is CallStmtDto -> {
            val expr = expr.toEtsEntity() as EtsCallExpr // safe cast
            EtsCallStmt(
                location = loc(),
                expr = expr,
            )
        }

        is ReturnStmtDto -> {
            val returnValue = ensureLocal(arg.toEtsEntity())
            EtsReturnStmt(
                location = loc(),
                returnValue = returnValue,
            )
        }

        is ReturnVoidStmtDto -> {
            EtsReturnStmt(
                location = loc(),
                returnValue = null,
            )
        }

        is ThrowStmtDto -> {
            val exception = ensureLocal(arg.toEtsEntity())
            EtsThrowStmt(
                location = loc(),
                exception = exception,
            )
        }

        is IfStmtDto -> {
            val condition = ensureLocal(condition.toEtsEntity())
            EtsIfStmt(
                location = loc(),
                condition = condition,
            )
        }

        is RawStmtDto -> {
            EtsRawStmt(
                location = loc(),
                kind = kind,
                extra = extra,
            )
        }
    }

    private fun ValueDto.toEtsEntity(): EtsEntity = when (this) {
        is LocalDto -> toEtsLocal()

        is ConstantDto -> toEtsConstant()

        is NewExprDto -> EtsNewExpr(
            type = classType.toEtsType(),
        )

        is NewArrayExprDto -> EtsNewArrayExpr(
            elementType = elementType.toEtsType(),
            size = size.toEtsEntity(),
        )

        is DeleteExprDto -> EtsDeleteExpr(
            arg = arg.toEtsEntity(),
        )

        is AwaitExprDto -> EtsAwaitExpr(
            arg = ensureLocal(arg.toEtsEntity()),
            type = type.toEtsType(),
        )

        is YieldExprDto -> EtsYieldExpr(
            arg = arg.toEtsEntity(),
            type = type.toEtsType(),
        )

        is TypeOfExprDto -> EtsTypeOfExpr(
            arg = arg.toEtsEntity(),
        )

        is InstanceOfExprDto -> EtsInstanceOfExpr(
            arg = arg.toEtsEntity(),
            checkType = checkType.toEtsType(),
        )

        is CastExprDto -> EtsCastExpr(
            arg = arg.toEtsEntity(),
            type = type.toEtsType(),
        )

        is UnaryOperationDto -> {
            val arg = arg.toEtsEntity()
            val type = type.toEtsType()
            when (op) {
                Ops.Unary.NOT -> EtsNotExpr(arg)
                Ops.Unary.BIT_NOT -> EtsBitNotExpr(arg, type)
                Ops.Unary.MINUS -> EtsNegExpr(arg, type)
                Ops.Unary.PLUS -> EtsUnaryPlusExpr(arg, type)
                Ops.Unary.INC -> EtsPreIncExpr(arg, type)
                Ops.Unary.DEC -> EtsPreDecExpr(arg, type)
                else -> error("Unknown unop: '$op'")
            }
        }

        is BinaryOperationDto -> {
            val left = left.toEtsEntity()
            val right = right.toEtsEntity()
            val type = type.toEtsType()
            when (op) {
                Ops.Binary.ADD -> EtsAddExpr(left, right, type)
                Ops.Binary.SUB -> EtsSubExpr(left, right, type)
                Ops.Binary.MUL -> EtsMulExpr(left, right, type)
                Ops.Binary.DIV -> EtsDivExpr(left, right, type)
                Ops.Binary.MOD -> EtsRemExpr(left, right, type)
                Ops.Binary.EXP -> EtsExpExpr(left, right, type)
                Ops.Binary.BIT_AND -> EtsBitAndExpr(left, right, type)
                Ops.Binary.BIT_OR -> EtsBitOrExpr(left, right, type)
                Ops.Binary.BIT_XOR -> EtsBitXorExpr(left, right, type)
                Ops.Binary.LSH -> EtsLeftShiftExpr(left, right, type)
                Ops.Binary.RSH -> EtsRightShiftExpr(left, right, type)
                Ops.Binary.URSH -> EtsUnsignedRightShiftExpr(left, right, type)
                Ops.Binary.AND -> EtsAndExpr(left, right, type)
                Ops.Binary.OR -> EtsOrExpr(left, right, type)
                Ops.Binary.NULLISH -> EtsNullishCoalescingExpr(left, right, type)
                else -> error("Unknown binop: $op")
            }
        }

        is RelationOperationDto -> {
            val left = left.toEtsEntity()
            val right = right.toEtsEntity()
            // Note: `type` is ignored here!
            when (op) {
                Ops.Relational.EQ -> EtsEqExpr(left, right)
                Ops.Relational.NOT_EQ -> EtsNotEqExpr(left, right)
                Ops.Relational.STRICT_EQ -> EtsStrictEqExpr(left, right)
                Ops.Relational.STRICT_NOT_EQ -> EtsStrictNotEqExpr(left, right)
                Ops.Relational.LT -> EtsLtExpr(left, right)
                Ops.Relational.LT_EQ -> EtsLtEqExpr(left, right)
                Ops.Relational.GT -> EtsGtExpr(left, right)
                Ops.Relational.GT_EQ -> EtsGtEqExpr(left, right)
                Ops.Relational.IN -> EtsInExpr(left, right)
                else -> error("Unknown relop: $op")
            }
        }

        is InstanceCallExprDto -> EtsInstanceCallExpr(
            instance = (instance as LocalDto).toEtsLocal(), // safe cast
            callee = method.toEtsMethodSignature(),
            args = args.map { ensureLocal(it.toEtsEntity()) },
            type = type.toEtsType(),
        )

        is StaticCallExprDto -> EtsStaticCallExpr(
            callee = method.toEtsMethodSignature(),
            args = args.map { ensureLocal(it.toEtsEntity()) },
            type = type.toEtsType(),
        )

        is PtrCallExprDto -> EtsPtrCallExpr(
            ptr = ensureLocal(ptr.toEtsEntity() as EtsValue), // safe cast
            callee = method.toEtsMethodSignature(),
            args = args.map { ensureLocal(it.toEtsEntity()) },
            type = type.toEtsType(),
        )

        is ThisRefDto -> EtsThis(
            type = type.toEtsType(),
        )

        is ParameterRefDto -> EtsParameterRef(
            index = index,
            type = type.toEtsType(),
        )

        is ArrayRefDto -> EtsArrayAccess(
            array = ensureLocal(array.toEtsEntity() as EtsValue), // safe cast
            index = index.toEtsEntity() as EtsValue, // safe cast
            type = type.toEtsType(),
        )

        is FieldRefDto -> toEtsFieldRef()

        is CaughtExceptionRefDto -> EtsCaughtExceptionRef(
            type = type.toEtsType(),
        )

        is GlobalRefDto -> EtsGlobalRef(
            name = name,
            ref = ref?.let { ensureLocal(it.toEtsEntity()) },
        )

        is ClosureFieldRefDto -> EtsClosureFieldRef(
            base = base.toEtsLocal(),
            fieldName = fieldName,
            type = type.toEtsType(),
        )

        is RawValueDto -> EtsRawEntity(
            kind = kind,
            extra = extra,
        )
    }

    private fun FieldRefDto.toEtsFieldRef(): EtsFieldRef {
        return when (this) {
            is InstanceFieldRefDto -> EtsInstanceFieldRef(
                instance = (instance as LocalDto).toEtsLocal(), // safe cast
                field = field.toEtsFieldSignature(),
                type = type.toEtsType(),
            )

            is StaticFieldRefDto -> EtsStaticFieldRef(
                field = field.toEtsFieldSignature(),
                type = type.toEtsType(),
            )
        }
    }

    private fun CfgDto.toEtsCfg(): EtsBlockCfg {
        if (blocks.isEmpty()) {
            return EtsBlockCfg.EMPTY
        }

        val blocks = this.blocks.map { block ->
            currentStmts = mutableListOf()
            for (stmt in block.stmts) {
                currentStmts += stmt.toEtsStmt()
            }
            if (currentStmts.isEmpty()) {
                currentStmts += EtsNopStmt(location = loc())
            }
            BasicBlock(block.id, currentStmts)
        }
        // Note: in DTO, successors for IF stmts are (false, true) branches,
        //       however in all our CFGs we use (true, false) order.
        // val successors = this.blocks.associate { it.id to it.successors.asReversed() }

        val successors = this.blocks.associate { it.id to it.successors }

        return EtsBlockCfg(
            blocks = blocks,
            successors = successors,
        )
    }
}

fun ClassDto.toEtsClass(): EtsClass {
    val signature = signature.toEtsClassSignature()
    val superClassSignature = superClassName?.takeIf { it != "" }?.let { name ->
        EtsClassSignature(
            name = name,
            file = EtsFileSignature.UNKNOWN,
        )
    }
    val implementedInterfaces = implementedInterfaceNames.map { name ->
        EtsClassSignature(
            name = name,
            file = EtsFileSignature.UNKNOWN,
        )
    }
    val fields = fields.map { it.toEtsField() }
    val methods = methods.map { it.toEtsMethod() }
    val category = category.toEtsClassCategory()
    val typeParameters = typeParameters?.map { it.toEtsType() } ?: emptyList()
    val modifiers = EtsModifiers(modifiers)
    val decorators = decorators.map { it.toEtsDecorator() }

    return EtsClassImpl(
        signature = signature,
        fields = fields,
        methods = methods,
        category = category,
        superClass = superClassSignature,
        implementedInterfaces = implementedInterfaces,
        typeParameters = typeParameters,
        modifiers = modifiers,
        decorators = decorators,
    )
}

fun TypeDto.toEtsType(): EtsType = when (this) {
    is AliasTypeDto -> EtsAliasType(
        name = name,
        originalType = originalType.toEtsType(),
        signature = signature.toEtsLocalSignature(),
    )

    AnyTypeDto -> EtsAnyType

    is ArrayTypeDto -> EtsArrayType(
        elementType = elementType.toEtsType(),
        dimensions = dimensions,
    )

    BooleanTypeDto -> EtsBooleanType

    is ClassTypeDto -> toEtsClassType()

    is EnumValueTypeDto -> EtsEnumValueType(
        signature = signature.toEtsClassSignature(),
        name = name,
    )

    is FunctionTypeDto -> EtsFunctionType(
        signature = signature.toEtsMethodSignature(),
        typeParameters = typeParameters.map { it.toEtsType() },
    )

    is GenericTypeDto -> EtsGenericType(
        typeName = name,
        constraint = constraint?.toEtsType(),
        defaultType = this@toEtsType.defaultType?.toEtsType(),
    )

    is IntersectionTypeDto -> EtsIntersectionType(
        types = types.map { it.toEtsType() },
    )

    is LexicalEnvTypeDto -> EtsLexicalEnvType(
        nestedMethod = method.toEtsMethodSignature(),
        closures = closures.map { it.toEtsLocal() },
    )

    is LiteralTypeDto -> when (val literalValue = literal) {
        is PrimitiveLiteralDto.StringLiteral -> EtsStringLiteralType(literalValue.value)
        is PrimitiveLiteralDto.NumberLiteral -> EtsNumberLiteralType(literalValue.value)
        is PrimitiveLiteralDto.BooleanLiteral -> EtsBooleanLiteralType(literalValue.value)
    }

    NeverTypeDto -> EtsNeverType

    NullTypeDto -> EtsNullType

    NumberTypeDto -> EtsNumberType

    StringTypeDto -> EtsStringType

    is TupleTypeDto -> EtsTupleType(
        types = types.map { it.toEtsType() },
    )

    is UnclearReferenceTypeDto -> EtsUnclearRefType(
        name = name,
        typeParameters = typeParameters.map { it.toEtsType() },
    )

    UndefinedTypeDto -> EtsUndefinedType

    is UnionTypeDto -> EtsUnionType(
        types = types.map { it.toEtsType() },
    )

    UnknownTypeDto -> EtsUnknownType

    VoidTypeDto -> EtsVoidType

    is RawTypeDto -> EtsRawType(
        kind = kind,
        extra = extra,
    )
}

fun ClassTypeDto.toEtsClassType(): EtsClassType {
    return EtsClassType(
        signature = signature.toEtsClassSignature(),
        typeParameters = typeParameters.map { it.toEtsType() },
    )
}

fun ConstantDto.toEtsConstant(): EtsConstant {
    return when (val type = type.toEtsType()) {
        EtsStringType -> EtsStringConstant(value = this.value)

        EtsBooleanType -> EtsBooleanConstant(value = value.toBoolean())

        EtsNumberType -> EtsNumberConstant(value = value.toDouble())

        EtsNullType -> EtsNullConstant

        EtsUndefinedType -> EtsUndefinedConstant

        else -> object : EtsConstant {
            val value: String = this@toEtsConstant.value

            override val type: EtsType = type

            override fun toString(): String {
                return value
            }

            override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
                return visitor.visit(this)
            }
        }
    }
}

fun FileSignatureDto.toEtsFileSignature(): EtsFileSignature {
    return EtsFileSignature(
        projectName = projectName,
        fileName = fileName,
    )
}

fun NamespaceSignatureDto.toEtsNamespaceSignature(): EtsNamespaceSignature {
    return EtsNamespaceSignature(
        name = name,
        file = declaringFile.toEtsFileSignature(),
        namespace = declaringNamespace?.toEtsNamespaceSignature(),
    )
}

fun ClassSignatureDto.toEtsClassSignature(): EtsClassSignature {
    return EtsClassSignature(
        name = name,
        file = declaringFile.toEtsFileSignature(),
        namespace = declaringNamespace?.toEtsNamespaceSignature(),
    )
}

fun FieldSignatureDto.toEtsFieldSignature(): EtsFieldSignature {
    return EtsFieldSignature(
        enclosingClass = declaringClass.toEtsClassSignature(),
        name = name,
        type = type.toEtsType(),
    )
}

fun MethodSignatureDto.toEtsMethodSignature(): EtsMethodSignature {
    return EtsMethodSignature(
        enclosingClass = declaringClass.toEtsClassSignature(),
        name = name,
        parameters = parameters.mapIndexed { index, param ->
            EtsMethodParameter(
                index = index,
                name = param.name,
                type = param.type.toEtsType(),
                isOptional = param.isOptional,
                isRest = param.isRest,
            )
        },
        returnType = returnType.toEtsType(),
    )
}

fun LocalSignatureDto.toEtsLocalSignature(): EtsLocalSignature {
    return EtsLocalSignature(
        name = name,
        method = method.toEtsMethodSignature(),
    )
}

fun MethodDto.toEtsMethod(): EtsMethod {
    val signature = signature.toEtsMethodSignature()
    val typeParameters = typeParameters?.map { it.toEtsType() } ?: emptyList()
    val modifiers = EtsModifiers(modifiers)
    val decorators = decorators.map { it.toEtsDecorator() }
    if (body != null) {
        val builder = EtsMethodBuilder(
            signature = signature,
            typeParameters = typeParameters,
            modifiers = modifiers,
            decorators = decorators,
            locals = body.locals.map { it.toEtsLocal() },
        )
        return builder.build(body.cfg, body.traps)
    } else {
        return EtsMethodImpl(
            signature = signature,
            typeParameters = typeParameters,
            modifiers = modifiers,
            decorators = decorators,
        )
    }
}

fun FieldDto.toEtsField(): EtsField {
    return EtsFieldImpl(
        signature = EtsFieldSignature(
            enclosingClass = signature.declaringClass.toEtsClassSignature(),
            name = signature.name,
            type = signature.type.toEtsType()
        ),
        modifiers = EtsModifiers(modifiers),
        isOptional = isOptional,
        isDefinitelyAssigned = isDefinitelyAssigned,
    )
}

fun NamespaceDto.toEtsNamespace(): EtsNamespace {
    val signature = signature.toEtsNamespaceSignature()
    val classes = classes.map { it.toEtsClass() }
    val namespaces = namespaces.map { it.toEtsNamespace() }
    return EtsNamespace(
        signature = signature,
        classes = classes,
        namespaces = namespaces,
    )
}

fun EtsFileDto.toEtsFile(): EtsFile {
    val signature = signature.toEtsFileSignature()
    val classes = classes.map { it.toEtsClass() }
    val namespaces = namespaces.map { it.toEtsNamespace() }
    val importInfos = importInfos.map { it.toEtsImportInfo() }
    val exportInfos = exportInfos.map { it.toEtsExportInfo() }
    return EtsFile(
        signature = signature,
        classes = classes,
        namespaces = namespaces,
        importInfos = importInfos,
        exportInfos = exportInfos,
    )
}

fun DecoratorDto.toEtsDecorator(): EtsDecorator {
    return EtsDecorator(
        name = kind,
        // TODO: content
        // TODO: param
    )
}

fun LocalDto.toEtsLocal(): EtsLocal {
    return EtsLocal(
        name = name,
        type = type.toEtsType(),
    )
}

fun ImportInfoDto.toEtsImportInfo(): EtsImportInfo {
    return EtsImportInfo(
        name = importName,
        type = when (importType) {
            "Identifier" -> EtsImportType.DEFAULT
            "NamedImports" -> EtsImportType.NAMED
            "NamespaceImport" -> EtsImportType.NAMESPACE
            "" -> EtsImportType.SIDE_EFFECT
            else -> error("Unknown import type: $importType")
        },
        from = importFrom,
        nameBeforeAs = nameBeforeAs,
        modifiers = EtsModifiers(modifiers),
    )
}

fun ExportInfoDto.toEtsExportInfo(): EtsExportInfo {
    return EtsExportInfo(
        name = exportName,
        type = exportType.toEtsExportType(),
        from = exportFrom,
        nameBeforeAs = nameBeforeAs,
        modifiers = EtsModifiers(modifiers),
    )
}

private fun Int.toEtsClassCategory(): EtsClassCategory {
    return when (this) {
        0 -> EtsClassCategory.CLASS
        1 -> EtsClassCategory.STRUCT
        2 -> EtsClassCategory.INTERFACE
        3 -> EtsClassCategory.ENUM
        4 -> EtsClassCategory.TYPE_LITERAL
        5 -> EtsClassCategory.OBJECT
        else -> error("Unknown class category: $this")
    }
}

private fun Int.toEtsExportType(): EtsExportType {
    return when (this) {
        0 -> EtsExportType.NAMESPACE
        1 -> EtsExportType.CLASS
        2 -> EtsExportType.METHOD
        3 -> EtsExportType.LOCAL
        4 -> EtsExportType.TYPE
        9 -> EtsExportType.UNKNOWN
        else -> {
            logger.warn { "Unknown export type value: $this, defaulting to UNKNOWN" }
            EtsExportType.UNKNOWN
        }
    }
}
