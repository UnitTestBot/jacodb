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

interface GoExprVisitor<T> {
    fun visitGoCallExpr(expr: GoCallExpr): T
    fun visitGoAllocExpr(expr: GoAllocExpr): T
    fun visitGoPhiExpr(expr: GoPhiExpr): T

    fun visitGoAddExpr(expr: GoAddExpr): T
    fun visitGoSubExpr(expr: GoSubExpr): T
    fun visitGoMulExpr(expr: GoMulExpr): T
    fun visitGoDivExpr(expr: GoDivExpr): T
    fun visitGoModExpr(expr: GoModExpr): T
    fun visitGoAndExpr(expr: GoAndExpr): T
    fun visitGoOrExpr(expr: GoOrExpr): T
    fun visitGoXorExpr(expr: GoXorExpr): T
    fun visitGoShlExpr(expr: GoShlExpr): T
    fun visitGoShrExpr(expr: GoShrExpr): T
    fun visitGoAndNotExpr(expr: GoAndNotExpr): T
    fun visitGoEqlExpr(expr: GoEqlExpr): T
    fun visitGoNeqExpr(expr: GoNeqExpr): T
    fun visitGoLssExpr(expr: GoLssExpr): T
    fun visitGoLeqExpr(expr: GoLeqExpr): T
    fun visitGoGtrExpr(expr: GoGtrExpr): T
    fun visitGoGeqExpr(expr: GoGeqExpr): T

    fun visitGoUnNotExpr(expr: GoUnNotExpr): T
    fun visitGoUnSubExpr(expr: GoUnSubExpr): T
    fun visitGoUnArrowExpr(expr: GoUnArrowExpr): T
    fun visitGoUnMulExpr(expr: GoUnMulExpr): T
    fun visitGoUnXorExpr(expr: GoUnXorExpr): T

    fun visitGoChangeTypeExpr(expr: GoChangeTypeExpr): T
    fun visitGoConvertExpr(expr: GoConvertExpr): T
    fun visitGoMultiConvertExpr(expr: GoMultiConvertExpr): T
    fun visitGoChangeInterfaceExpr(expr: GoChangeInterfaceExpr): T
    fun visitGoSliceToArrayPointerExpr(expr: GoSliceToArrayPointerExpr): T
    fun visitGoMakeInterfaceExpr(expr: GoMakeInterfaceExpr): T
    fun visitGoMakeClosureExpr(expr: GoMakeClosureExpr): T
    fun visitGoMakeMapExpr(expr: GoMakeMapExpr): T
    fun visitGoMakeChanExpr(expr: GoMakeChanExpr): T
    fun visitGoMakeSliceExpr(expr: GoMakeSliceExpr): T
    fun visitGoSliceExpr(expr: GoSliceExpr): T
    fun visitGoFieldAddrExpr(expr: GoFieldAddrExpr): T
    fun visitGoFieldExpr(expr: GoFieldExpr): T
    fun visitGoIndexAddrExpr(expr: GoIndexAddrExpr): T
    fun visitGoIndexExpr(expr: GoIndexExpr): T
    fun visitGoLookupExpr(expr: GoLookupExpr): T
    fun visitGoSelectExpr(expr: GoSelectExpr): T
    fun visitGoRangeExpr(expr: GoRangeExpr): T
    fun visitGoNextExpr(expr: GoNextExpr): T
    fun visitGoTypeAssertExpr(expr: GoTypeAssertExpr): T
    fun visitGoExtractExpr(expr: GoExtractExpr): T

    fun visitGoVar(expr: GoVar): T
    fun visitGoFreeVar(expr: GoFreeVar): T
    fun visitGoParameter(expr: GoParameter): T
    fun visitGoConst(expr: GoConst): T
    fun visitGoGlobal(expr: GoGlobal): T
    fun visitGoBuiltin(expr: GoBuiltin): T
    fun visitGoFunction(expr: GoFunction): T

    fun visitGoBool(value: GoBool): T
    fun visitGoInt(value: GoInt): T
    fun visitGoInt8(value: GoInt8): T
    fun visitGoInt16(value: GoInt16): T
    fun visitGoInt32(value: GoInt32): T
    fun visitGoInt64(value: GoInt64): T
    fun visitGoUInt(value: GoUInt): T
    fun visitGoUInt8(value: GoUInt8): T
    fun visitGoUInt16(value: GoUInt16): T
    fun visitGoUInt32(value: GoUInt32): T
    fun visitGoUInt64(value: GoUInt64): T
    fun visitGoFloat32(value: GoFloat32): T
    fun visitGoFloat64(value: GoFloat64): T
    fun visitGoNullConstant(value: GoNullConstant): T
    fun visitGoStringConstant(value: GoStringConstant): T
}
