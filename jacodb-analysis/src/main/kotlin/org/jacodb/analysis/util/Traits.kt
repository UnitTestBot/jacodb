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

package org.jacodb.analysis.util

import org.jacodb.analysis.ifds.AccessPath
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.CommonProject
import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.taint.configuration.ConstantValue

/**
 * Extensions for analysis.
 */
interface Traits<out Project, out Method, out Statement, out Value, out Expr, out CallExpr, out MethodParameter>
    where Project : CommonProject,
          Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement>,
          Value : CommonValue,
          Expr : CommonExpr,
          CallExpr : CommonCallExpr,
          MethodParameter : CommonMethodParameter {

    val @UnsafeVariance Method.thisInstance: CommonThis
    val @UnsafeVariance Method.isConstructor: Boolean

    fun @UnsafeVariance Expr.toPathOrNull(): AccessPath?
    fun @UnsafeVariance Value.toPathOrNull(): AccessPath?
    fun @UnsafeVariance Value.toPath(): AccessPath

    val @UnsafeVariance CallExpr.callee: Method

    fun getArgument(project: @UnsafeVariance Project, param: @UnsafeVariance MethodParameter): CommonArgument?
    fun getArguments(project: @UnsafeVariance Project, method: @UnsafeVariance Method): List<CommonArgument>

    fun @UnsafeVariance Value.isConstant(): Boolean
    fun @UnsafeVariance Value.eqConstant(constant: ConstantValue): Boolean
    fun @UnsafeVariance Value.ltConstant(constant: ConstantValue): Boolean
    fun @UnsafeVariance Value.gtConstant(constant: ConstantValue): Boolean
    fun @UnsafeVariance Value.matches(pattern: String): Boolean
}
