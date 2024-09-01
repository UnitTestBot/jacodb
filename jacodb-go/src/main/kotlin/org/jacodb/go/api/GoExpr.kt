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

package org.jacodb.go.api

import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr

interface GoExpr : CommonExpr {
    val type: GoType

    fun <T> accept(visitor: GoExprVisitor<T>): T // TODO visitor for CoreExpr?
}

interface GoBinaryExpr : GoExpr, GoValue, GoAssignableInst {
    val lhv: GoValue
    val rhv: GoValue
}

interface GoConditionExpr : GoExpr

data class GoAllocExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "new ${type.typeName}"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAllocExpr(this)
    }
}

data class GoAddExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAddExpr(this)
    }
}

data class GoSubExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSubExpr(this)
    }
}

data class GoMulExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMulExpr(this)
    }
}

data class GoDivExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoDivExpr(this)
    }
}

data class GoModExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoModExpr(this)
    }
}

data class GoAndExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv & $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAndExpr(this)
    }
}

data class GoOrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv | $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoOrExpr(this)
    }
}

data class GoXorExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv ^ $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoXorExpr(this)
    }
}

data class GoShlExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv << $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoShlExpr(this)
    }
}

data class GoShrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv >> $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoShrExpr(this)
    }
}

data class GoAndNotExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv &^ $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAndNotExpr(this)
    }
}

data class GoEqlExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoEqlExpr(this)
    }
}

data class GoNeqExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoNeqExpr(this)
    }
}

data class GoLssExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLssExpr(this)
    }
}

data class GoLeqExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLeqExpr(this)
    }
}

data class GoGtrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoGtrExpr(this)
    }
}

data class GoGeqExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoGeqExpr(this)
    }
}

interface GoUnaryExpr : GoExpr, GoValue, GoAssignableInst {
    val value: GoValue
}

data class GoUnNotExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    override val name: String,
) : GoUnaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "!$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnNotExpr(this)
    }
}

data class GoUnSubExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    override val name: String,
) : GoUnaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "-$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnSubExpr(this)
    }
}

data class GoUnArrowExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    val commaOk: Boolean,
    override val name: String,
) : GoUnaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "<-$value${if (commaOk) ",ok" else ""}"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnArrowExpr(this)
    }
}

data class GoUnMulExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    override val name: String,
) : GoUnaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "*$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnMulExpr(this)
    }
}

data class GoUnXorExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    override val name: String,
) : GoUnaryExpr {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "^$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnXorExpr(this)
    }
}

data class GoCallExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val value: GoValue,
    override val args: List<GoValue>,
    val callee: GoMethod?,
    override val name: String,
) : GoExpr, GoValue, CommonCallExpr, GoAssignableInst {
    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoCallExpr(this)
    }

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String {
        var receiver = ""
        if (callee != null) {
            receiver = "${callee.metName}."
        }
        return "$receiver${value}(${args.joinToString { it.toString() }})"
    }
}

data class GoPhiExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    var edges: List<GoValue>,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String {
        var res = "phi ["
        edges.forEachIndexed { i, edge ->
            res += if (edge is GoAssignableInst) {
                edge.name
            } else {
                edge.toString()
            }
            if (i + 1 != edges.size) {
                res += ", "
            }
        }
        res += "]"
        return res
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoPhiExpr(this)
    }
}

data class GoChangeTypeExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "${type.typeName} -> $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoChangeTypeExpr(this)
    }
}

data class GoConvertExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "(${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoConvertExpr(this)
    }
}

data class GoMultiConvertExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "multi (${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMultiConvertExpr(this)
    }
}

data class GoChangeInterfaceExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "change interface (${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoChangeInterfaceExpr(this)
    }
}

data class GoSliceToArrayPointerExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "slice to array pointer (${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSliceToArrayPointerExpr(this)
    }
}

data class GoMakeInterfaceExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val value: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "interface{} <- ${type.typeName} ($value)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeInterfaceExpr(this)
    }
}

data class GoMakeClosureExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val func: GoMethod,
    val bindings: List<GoValue>,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "make closure $func [$bindings]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeClosureExpr(this)
    }
}

data class GoMakeSliceExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val len: GoValue,
    val cap: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "new []${type.typeName}($len, $cap)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeSliceExpr(this)
    }
}

data class GoSliceExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val array: GoValue,
    val low: GoValue,
    val high: GoValue,
    val max: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "slice $array[$low:$high]:$max"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSliceExpr(this)
    }
}

data class GoMakeMapExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val reserve: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "make map ${type.typeName} ($reserve)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeMapExpr(this)
    }
}

data class GoMakeChanExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val size: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "make chan ${type.typeName} ($size)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeChanExpr(this)
    }
}

data class GoFieldAddrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val field: Int,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "addr $instance.[${field}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFieldAddrExpr(this)
    }
}

data class GoFieldExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val field: Int,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "$instance.[${field}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFieldExpr(this)
    }
}

data class GoIndexAddrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val index: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "addr ${instance}[${index}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoIndexAddrExpr(this)
    }
}

data class GoIndexExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val index: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "${instance}[${index}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoIndexExpr(this)
    }
}

data class GoLookupExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val index: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "lookup ${instance}[${index}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLookupExpr(this)
    }
}

data class GoSelectExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val chans: List<GoValue>,
    val sends: List<GoValue?>,
    val blocking: Boolean,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "select ${if (blocking) "blocking" else "nonblocking"} [$chans, $sends]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSelectExpr(this)
    }
}

data class GoRangeExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "range ${instance}:${type.typeName}"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoRangeExpr(this)
    }
}

data class GoNextExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "next $instance"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoNextExpr(this)
    }
}

data class GoTypeAssertExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val assertType: GoType,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "typeassert $instance.($assertType)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoTypeAssertExpr(this)
    }
}

data class GoExtractExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val index: Int,
    override val name: String,
) : GoValue, GoAssignableInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = this
        )
    }

    override fun toString(): String = "extract $instance [$index]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoExtractExpr(this)
    }
}

interface GoSimpleValue : GoValue

interface GoLocal : GoSimpleValue {
    val name: String
}

data class GoVar(override val name: String, override val type: GoType) : GoLocal, GoConditionExpr {
    override fun toString(): String = name

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoVar(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoVar

        return type == other.type
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoFreeVar(val index: Int, override val name: String, override val type: GoType) : GoLocal, GoConditionExpr {
    override fun toString(): String = name

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFreeVar(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoFreeVar

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoConst(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = "const $name"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoConst(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoConst

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoGlobal(val index: Int, override val name: String, override val type: GoType) : GoLocal, GoConditionExpr {
    override fun toString(): String = "global $name"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoGlobal(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoGlobal

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoBuiltin(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = "builtin $name"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoBuiltin(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoBuiltin

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoFunction(
    override val type: GoType,
    override val parameters: List<GoParameter>,
    override val metName: String,
    override var blocks: List<GoBasicBlock>,
    override val packageName: String,
    val returnTypes: List<GoType>,
) : GoMethod {
    private var flowGraph: GoGraph? = null

    override fun toString(): String = "${packageName}::${metName}${
        parameters.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", "
        )
    }:${
        returnTypes.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", "
        )
    }"

    override fun flowGraph(): GoGraph {
        if (flowGraph == null) {
            val blocksNum = mutableListOf<Int>()
            blocks.forEachIndexed { index, b ->
                for (i in b.instructions) {
                    blocksNum.add(index)
                }
            }
            flowGraph = GoBlockGraph(
                blocks,
                blocks.flatMap { it.instructions },
                blocksNum,
            ).graph
        }
        return flowGraph!!
    }

    override fun hashCode(): Int {
        return packageName.hashCode() * 31 + metName.hashCode()
    }

    override val name: String
        get() = metName
    override val returnType: GoType
        get() = TupleType(
            returnTypes.map { it.typeName }
        )

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFunction(this)
    }
}

data class GoParameter(val index: Int, override val name: String, override val type: GoType) : GoLocal, CommonMethodParameter, GoConditionExpr {
    companion object {
        @JvmStatic
        fun of(index: Int, name: String?, type: GoType): GoParameter {
            return GoParameter(index, name ?: "arg$$index", type)
        }
    }

    override fun toString(): String = name

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoParameter(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoParameter

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

interface GoConstant : GoSimpleValue

interface GoNumericConstant : GoConstant {
    val value: Number

    fun isEqual(c: GoNumericConstant): Boolean = c.value == value

    fun isNotEqual(c: GoNumericConstant): Boolean = !isEqual(c)

    fun isLessThan(c: GoNumericConstant): Boolean

    fun isLessThanOrEqual(c: GoNumericConstant): Boolean = isLessThan(c) || isEqual(c)

    fun isGreaterThan(c: GoNumericConstant): Boolean

    fun isGreaterThanOrEqual(c: GoNumericConstant): Boolean = isGreaterThan(c) || isEqual(c)

    operator fun plus(c: GoNumericConstant): GoNumericConstant

    operator fun minus(c: GoNumericConstant): GoNumericConstant

    operator fun times(c: GoNumericConstant): GoNumericConstant

    operator fun div(c: GoNumericConstant): GoNumericConstant

    operator fun rem(c: GoNumericConstant): GoNumericConstant

    operator fun unaryMinus(): GoNumericConstant

}

data class GoBool(val value: Boolean, override val type: GoType) : GoConstant, GoConditionExpr {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoBool(this)
    }
}

data class GoInt(override val value: Long, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value + c.value.toLong(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value - c.value.toLong(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value * c.value.toLong(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value / c.value.toLong(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value % c.value.toLong(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toLong()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toLong()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoInt(this)
    }
}

data class GoInt8(override val value: Byte, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value + c.value.toByte(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value - c.value.toByte(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value * c.value.toByte(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value / c.value.toByte(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value % c.value.toByte(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt32(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toByte()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toByte()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoInt8(this)
    }
}

data class GoInt16(override val value: Short, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value + c.value.toShort(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value - c.value.toShort(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value * c.value.toShort(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value / c.value.toShort(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value % c.value.toShort(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt32(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toShort()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toShort()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoInt16(this)
    }
}

data class GoInt32(override val value: Int, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value + c.value.toInt(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value - c.value.toInt(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value * c.value.toInt(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value / c.value.toInt(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt32(value % c.value.toInt(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt32(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toInt()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toInt()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoInt32(this)
    }
}

data class GoInt64(override val value: Long, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt64(value + c.value.toLong(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt64(value - c.value.toLong(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt64(value * c.value.toLong(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt64(value / c.value.toLong(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt64(value % c.value.toLong(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt64(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toLong()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toLong()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoInt64(this)
    }
}

data class GoUInt(val value: ULong, override val type: GoType) : GoConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUInt(this)
    }
}

data class GoUInt8(val value: UByte, override val type: GoType) : GoConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUInt8(this)
    }
}

data class GoUInt16(val value: UShort, override val type: GoType) : GoConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUInt16(this)
    }
}

data class GoUInt32(val value: UInt, override val type: GoType) : GoConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUInt32(this)
    }
}

data class GoUInt64(val value: ULong, override val type: GoType) : GoConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUInt64(this)
    }
}

data class GoFloat32(override val value: Float, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoFloat32(value + c.value.toFloat(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoFloat32(value - c.value.toFloat(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoFloat32(value * c.value.toFloat(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoFloat32(value / c.value.toFloat(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoFloat32(value % c.value.toFloat(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoFloat32(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toFloat()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toFloat()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFloat32(this)
    }
}

data class GoFloat64(override val value: Double, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoFloat64(value + c.value.toDouble(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoFloat64(value.div(c.value.toDouble()), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoFloat64(value * c.value.toDouble(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoFloat64(value.div(c.value.toDouble()), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoFloat64(value.rem(c.value.toDouble()), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoFloat64(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toDouble()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toDouble()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFloat64(this)
    }
}

class GoNullConstant : GoConstant {
    override val type: GoType = NullType()

    override fun toString(): String = "null"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoNullConstant(this)
    }
}

data class GoStringConstant(val value: String, override val type: GoType) : GoConstant {
    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoStringConstant(this)
    }
}
