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

package org.jacodb.analysis.impl

// ответственность за то, чтобы при создании ребер между датафлоу фактами
// при рассматривании разных случаев ребер в IFDS(при вызове метода, при возврате из метода и тд)
// лежит на создателе флоу функции.

// мы подразумеваем, что в флоу фанке нужно уметь считать домены для инструкций, которые выполнятся друг за другом напрямую
fun interface FlowFunctionInstance<D> {
    fun compute(fact: D): Collection<D>
}

interface FlowFunctionsSpace<Method, Statement, D> {
    fun obtainStartFacts(startStatement: Statement): Collection<D>
    fun obtainSequentFlowFunction(current: Statement, next: Statement): FlowFunctionInstance<D>
    fun obtainCallToStartFlowFunction(callStatement: Statement, callee: Method): FlowFunctionInstance<D>
    fun obtainCallToReturnFlowFunction(callStatement: Statement, returnSite: Statement): FlowFunctionInstance<D>
    fun obtainExitToReturnSiteFlowFunction(callStatement: Statement, returnSite: Statement, exitStatement: Statement): FlowFunctionInstance<D>
}