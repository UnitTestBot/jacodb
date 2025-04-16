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

import org.jacodb.ets.base.CONSTRUCTOR_NAME
import org.jacodb.ets.base.EtsAddExpr
import org.jacodb.ets.base.EtsAliasType
import org.jacodb.ets.base.EtsAndExpr
import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsAwaitExpr
import org.jacodb.ets.base.EtsBitAndExpr
import org.jacodb.ets.base.EtsBitNotExpr
import org.jacodb.ets.base.EtsBitOrExpr
import org.jacodb.ets.base.EtsBitXorExpr
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsCallExpr
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsCommaExpr
import org.jacodb.ets.base.EtsConstant
import org.jacodb.ets.base.EtsDeleteExpr
import org.jacodb.ets.base.EtsDivExpr
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsEqExpr
import org.jacodb.ets.base.EtsExpExpr
import org.jacodb.ets.base.EtsExpr
import org.jacodb.ets.base.EtsFieldRef
import org.jacodb.ets.base.EtsFunctionType
import org.jacodb.ets.base.EtsGenericType
import org.jacodb.ets.base.EtsGotoStmt
import org.jacodb.ets.base.EtsGtEqExpr
import org.jacodb.ets.base.EtsGtExpr
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsInExpr
import org.jacodb.ets.base.EtsInstLocation
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLeftShiftExpr
import org.jacodb.ets.base.EtsLengthExpr
import org.jacodb.ets.base.EtsLiteralType
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsLtEqExpr
import org.jacodb.ets.base.EtsLtExpr
import org.jacodb.ets.base.EtsMulExpr
import org.jacodb.ets.base.EtsNegExpr
import org.jacodb.ets.base.EtsNeverType
import org.jacodb.ets.base.EtsNewArrayExpr
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsNotEqExpr
import org.jacodb.ets.base.EtsNotExpr
import org.jacodb.ets.base.EtsNullConstant
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsNullishCoalescingExpr
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsOrExpr
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsPreDecExpr
import org.jacodb.ets.base.EtsPreIncExpr
import org.jacodb.ets.base.EtsPtrCallExpr
import org.jacodb.ets.base.EtsRawEntity
import org.jacodb.ets.base.EtsRawStmt
import org.jacodb.ets.base.EtsRawType
import org.jacodb.ets.base.EtsRemExpr
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsRightShiftExpr
import org.jacodb.ets.base.EtsStaticCallExpr
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStrictEqExpr
import org.jacodb.ets.base.EtsStrictNotEqExpr
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsSubExpr
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsThrowStmt
import org.jacodb.ets.base.EtsTupleType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsTypeOfExpr
import org.jacodb.ets.base.EtsUnaryPlusExpr
import org.jacodb.ets.base.EtsUnclearRefType
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnionType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.base.EtsUnsignedRightShiftExpr
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.base.EtsVoidType
import org.jacodb.ets.base.EtsYieldExpr
import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsDecorator
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFieldSubSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsLocalSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsModifiers
import org.jacodb.ets.model.EtsNamespace
import org.jacodb.ets.model.EtsNamespaceSignature

class EtsMethodBuilder(
    signature: EtsMethodSignature,
    typeParameters: List<EtsType> = emptyList(),
    locals: List<EtsLocal> = emptyList(),
    modifiers: EtsModifiers = EtsModifiers.EMPTY,
    decorators: List<EtsDecorator> = emptyList(),
) {
    private val etsMethod = EtsMethodImpl(signature, typeParameters, locals, modifiers, decorators)

    private val currentStmts: MutableList<EtsStmt> = mutableListOf()

    private var freeTempLocal: Int = 0

    private fun newTempLocal(type: EtsType): EtsLocal {
        return EtsLocal("_tmp${freeTempLocal++}", type)
    }

    private fun loc(): EtsInstLocation {
        return EtsInstLocation(etsMethod, currentStmts.size)
    }

    private var built: Boolean = false

    fun build(cfgDto: CfgDto): EtsMethod {
        require(!built) { "Method has already been built" }
        val cfg = cfgDto.toEtsCfg()
        etsMethod._cfg = cfg
        built = true
        return etsMethod
    }

    private fun ensureLocal(entity: EtsEntity): EtsLocal {
        if (entity is EtsLocal) {
            return entity
        }
        val newLocal = newTempLocal(entity.type)
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
            val returnValue = ensureOneAddress(arg.toEtsEntity())
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
            val arg = arg.toEtsEntity()
            EtsThrowStmt(
                location = loc(),
                arg = arg,
            )
        }

        is GotoStmtDto -> {
            EtsGotoStmt(location = loc())
        }

        is IfStmtDto -> {
            val condition = condition.toEtsEntity()
            EtsIfStmt(
                location = loc(),
                condition = condition,
            )
        }

        is SwitchStmtDto -> {
            val arg = arg.toEtsEntity()
            val cases = cases.map { it.toEtsEntity() }
            EtsSwitchStmt(
                location = loc(),
                arg = arg,
                cases = cases,
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
            type = classType.toEtsType() // TODO: safe cast to ClassType
        )

        is NewArrayExprDto -> EtsNewArrayExpr(
            elementType = elementType.toEtsType(),
            size = size.toEtsEntity(),
        )

        is DeleteExprDto -> EtsDeleteExpr(
            arg = arg.toEtsEntity(),
        )

        is AwaitExprDto -> EtsAwaitExpr(
            arg = arg.toEtsEntity(),
        )

        is YieldExprDto -> EtsYieldExpr(
            arg = arg.toEtsEntity(),
        )

        is TypeOfExprDto -> EtsTypeOfExpr(
            arg = arg.toEtsEntity(),
        )

        is InstanceOfExprDto -> EtsInstanceOfExpr(
            arg = arg.toEtsEntity(),
            checkType = checkType.toEtsType(),
        )

        is LengthExprDto -> EtsLengthExpr(
            arg = arg.toEtsEntity(),
        )

        is CastExprDto -> EtsCastExpr(
            arg = arg.toEtsEntity(),
            type = type.toEtsType(),
        )

        is UnaryOperationDto -> {
            val arg = arg.toEtsEntity()
            // Note: `type` is ignored here!
            when (op) {
                Ops.Unary.NOT -> EtsNotExpr(arg)
                Ops.Unary.BIT_NOT -> EtsBitNotExpr(arg.type, arg)
                Ops.Unary.MINUS -> EtsNegExpr(arg.type, arg)
                Ops.Unary.PLUS -> EtsUnaryPlusExpr(arg)
                Ops.Unary.INC -> EtsPreIncExpr(arg.type, arg)
                Ops.Unary.DEC -> EtsPreDecExpr(arg.type, arg)
                else -> error("Unknown unop: '$op'")
            }
        }

        is BinaryOperationDto -> {
            val left = left.toEtsEntity()
            val right = right.toEtsEntity()
            val type = type.toEtsType()
            when (op) {
                Ops.Binary.ADD -> EtsAddExpr(type, left, right)
                Ops.Binary.SUB -> EtsSubExpr(type, left, right)
                Ops.Binary.MUL -> EtsMulExpr(type, left, right)
                Ops.Binary.DIV -> EtsDivExpr(type, left, right)
                Ops.Binary.MOD -> EtsRemExpr(type, left, right)
                Ops.Binary.EXP -> EtsExpExpr(type, left, right)
                Ops.Binary.BIT_AND -> EtsBitAndExpr(type, left, right)
                Ops.Binary.BIT_OR -> EtsBitOrExpr(type, left, right)
                Ops.Binary.BIT_XOR -> EtsBitXorExpr(type, left, right)
                Ops.Binary.LSH -> EtsLeftShiftExpr(type, left, right)
                Ops.Binary.RSH -> EtsRightShiftExpr(type, left, right)
                Ops.Binary.URSH -> EtsUnsignedRightShiftExpr(type, left, right)
                Ops.Binary.AND -> EtsAndExpr(type, left, right)
                Ops.Binary.OR -> EtsOrExpr(type, left, right)
                Ops.Binary.NULLISH -> EtsNullishCoalescingExpr(type, left, right)
                Ops.Binary.COMMA -> EtsCommaExpr(left, right) // Note: `type` is ignored here!
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
            method = method.toEtsMethodSignature(),
            args = args.map { ensureLocal(it.toEtsEntity()) },
        )

        is StaticCallExprDto -> EtsStaticCallExpr(
            method = method.toEtsMethodSignature(),
            args = args.map { ensureLocal(it.toEtsEntity()) },
        )

        is PtrCallExprDto -> EtsPtrCallExpr(
            ptr = ensureLocal(ptr.toEtsEntity() as EtsValue), // safe cast
            method = method.toEtsMethodSignature(),
            args = args.map { ensureLocal(it.toEtsEntity()) },
        )

        is ThisRefDto -> EtsThis(
            type = (type as ClassTypeDto).toEtsClassType(), // safe cast
        )

        is ParameterRefDto -> EtsParameterRef(
            index = index,
            type = type.toEtsType(),
        )

        is ArrayRefDto -> EtsArrayAccess(
            array = array.toEtsEntity() as EtsValue, // TODO: check whether the cast is safe
            index = index.toEtsEntity() as EtsValue, // TODO: check whether the cast is safe
            type = type.toEtsType(),
        )

        is FieldRefDto -> toEtsFieldRef()

        is RawValueDto -> EtsRawEntity(
            kind = kind,
            extra = extra,
            type = type.toEtsType(),
        )
    }

    private fun FieldRefDto.toEtsFieldRef(): EtsFieldRef {
        val field = field.toEtsFieldSignature()
        return when (this) {
            is InstanceFieldRefDto -> EtsInstanceFieldRef(
                instance = (instance as LocalDto).toEtsLocal(), // safe cast
                field = field,
            )

            is StaticFieldRefDto -> EtsStaticFieldRef(
                field = field,
            )
        }
    }

    private fun CfgDto.toEtsCfg(): EtsCfg {
        require(blocks.isNotEmpty()) {
            "Method body should contain at least return stmt"
        }

        val visited: MutableSet<Int> = hashSetOf(0)
        val queue: ArrayDeque<Int> = ArrayDeque()
        queue.add(0)

        val blocks = blocks.associateBy { it.id }
        val blockStart: MutableMap<Int, Int> = hashMapOf()
        val blockEnd: MutableMap<Int, Int> = hashMapOf()

        while (queue.isNotEmpty()) {
            val block = blocks[queue.removeFirst()]!!
            blockStart[block.id] = currentStmts.size
            if (block.stmts.isNotEmpty()) {
                for (stmt in block.stmts) {
                    currentStmts += stmt.toEtsStmt()
                }
            } else {
                currentStmts += EtsNopStmt(loc())
            }
            blockEnd[block.id] = currentStmts.lastIndex
            check(blockStart[block.id]!! <= blockEnd[block.id]!!)

            for (next in block.successors) {
                if (visited.add(next)) {
                    queue.addLast(next)
                }
            }
        }

        val successorMap: MutableMap<EtsStmt, List<EtsStmt>> = hashMapOf()
        for (block in this.blocks) {
            val startId = blockStart[block.id]!!
            val endId = blockEnd[block.id]!!
            for (i in startId until endId) {
                successorMap[currentStmts[i]] = listOf(currentStmts[i + 1])
            }
            successorMap[currentStmts[endId]] = block.successors.mapNotNull { blockId ->
                blockStart[blockId]?.let { currentStmts[it] }
            }
        }

        return EtsCfg(
            stmts = currentStmts,
            successorMap = successorMap,
        )
    }
}

fun ClassDto.toEtsClass(): EtsClass {
    fun defaultConstructorDto(classSignatureDto: ClassSignatureDto): MethodDto {
        val zeroBlock = BasicBlockDto(
            id = 0,
            successors = emptyList(),
            predecessors = emptyList(),
            stmts = listOf(
                ReturnVoidStmtDto,
            ),
        )
        val cfg = CfgDto(blocks = listOf(zeroBlock))
        val body = BodyDto(locals = emptyList(), cfg = cfg)
        val signature = MethodSignatureDto(
            declaringClass = classSignatureDto,
            name = CONSTRUCTOR_NAME,
            parameters = emptyList(),
            returnType = ClassTypeDto(classSignatureDto),
        )
        return MethodDto(
            signature = signature,
            modifiers = 0,
            decorators = emptyList(),
            typeParameters = emptyList(),
            body = body,
        )
    }

    val signature = signature.toEtsClassSignature()
    val superClassSignature = superClassName?.takeIf { it != "" }?.let { name ->
        EtsClassSignature(
            name = name,
            file = EtsFileSignature.DEFAULT,
        )
    }
    val implementedInterfaces = implementedInterfaceNames.map { name ->
        EtsClassSignature(
            name = name,
            file = EtsFileSignature.DEFAULT,
        )
    }

    val fields = fields.map { it.toEtsField() }

    val (methodDtos, ctorDtos) = methods.partition { it.signature.name != CONSTRUCTOR_NAME }
    check(ctorDtos.size <= 1) { "Class should not have multiple constructors" }
    val ctorDto = ctorDtos.firstOrNull() ?: defaultConstructorDto(this.signature)

    val methods = methodDtos.map { it.toEtsMethod() }
    val ctor = ctorDto.toEtsMethod()

    val category = category.toEtsClassCategory()
    val typeParameters = typeParameters?.map { it.toEtsType() } ?: emptyList()

    val modifiers = EtsModifiers(modifiers)
    val decorators = decorators.map { it.toEtsDecorator() }

    return EtsClassImpl(
        signature = signature,
        fields = fields,
        methods = methods,
        ctor = ctor,
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

    is FunctionTypeDto -> EtsFunctionType(
        method = signature.toEtsMethodSignature(),
        typeParameters = typeParameters.map { it.toEtsType() },
    )

    is GenericTypeDto -> EtsGenericType(
        name = name,
        defaultType = defaultType?.toEtsType(),
        constraint = constraint?.toEtsType(),
    )

    is LiteralTypeDto -> EtsLiteralType(
        literalTypeName = literal.toString(),
    )

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
    return when (type.toEtsType()) {
        EtsStringType -> EtsStringConstant(value = this.value)

        EtsBooleanType -> EtsBooleanConstant(value = value.toBoolean())

        EtsNumberType -> EtsNumberConstant(value = value.toDouble())

        EtsNullType -> EtsNullConstant

        EtsUndefinedType -> EtsUndefinedConstant

        else -> object : EtsConstant {
            override val type: EtsType = EtsUnknownType

            override fun toString(): String = "Unknown($value)"

            override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
                if (visitor is EtsValue.Visitor.Default<R>) {
                    return visitor.defaultVisit(this)
                }
                error("Cannot handle $this")
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
        sub = EtsFieldSubSignature(
            name = name,
            type = type.toEtsType(),
        ),
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
        val locals = body.locals.map {
            it.toEtsLocal()
        }
        val builder = EtsMethodBuilder(
            signature = signature,
            typeParameters = typeParameters,
            locals = locals,
            modifiers = modifiers,
            decorators = decorators,
        )
        return builder.build(body.cfg)
    } else {
        return EtsMethodImpl(
            signature = signature,
            typeParameters = typeParameters,
            locals = emptyList(),
            modifiers = modifiers,
            decorators = decorators,
        )
    }
}

fun FieldDto.toEtsField(): EtsField {
    return EtsFieldImpl(
        signature = EtsFieldSignature(
            enclosingClass = signature.declaringClass.toEtsClassSignature(),
            sub = EtsFieldSubSignature(
                name = signature.name,
                type = signature.type.toEtsType(),
            ),
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
    return EtsFile(
        signature = signature,
        classes = classes,
        namespaces = namespaces,
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
