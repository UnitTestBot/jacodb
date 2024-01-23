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

@file:JvmName("RunnersLibrary")

package org.jacodb.analysis.library

import org.jacodb.analysis.engine.BaseIfdsUnitRunnerFactory
import org.jacodb.analysis.engine.BidiIfdsUnitRunnerFactory
import org.jacodb.analysis.library.analyzers.JcAliasAnalyzerFactory
import org.jacodb.analysis.library.analyzers.JcNpeAnalyzerFactory
import org.jacodb.analysis.library.analyzers.jcNpePrecalcBackwardAnalyzerFactory
import org.jacodb.analysis.library.analyzers.JcSqlInjectionAnalyzerFactory
import org.jacodb.analysis.library.analyzers.JcSqlInjectionBackwardAnalyzerFactory
import org.jacodb.analysis.library.analyzers.TaintAnalysisNode
import org.jacodb.analysis.library.analyzers.TaintNode
import org.jacodb.analysis.library.analyzers.JcUnusedVariableAnalyzerFactory
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstLocation

//TODO: add docs here
val UnusedVariableRunnerFactory =
    BaseIfdsUnitRunnerFactory<JcMethod, JcInstLocation, JcInst>(JcUnusedVariableAnalyzerFactory)

fun newJcSqlInjectionRunnerFactory(maxPathLength: Int = 5) =
    BidiIfdsUnitRunnerFactory<JcMethod, JcInstLocation, JcInst>(
        BaseIfdsUnitRunnerFactory(JcSqlInjectionAnalyzerFactory(maxPathLength)),
        BaseIfdsUnitRunnerFactory(JcSqlInjectionBackwardAnalyzerFactory(maxPathLength)),
    )

fun newJcNpeRunnerFactory(maxPathLength: Int = 5) = BidiIfdsUnitRunnerFactory<JcMethod, JcInstLocation, JcInst>(
    BaseIfdsUnitRunnerFactory(JcNpeAnalyzerFactory(maxPathLength)),
    BaseIfdsUnitRunnerFactory(jcNpePrecalcBackwardAnalyzerFactory(maxPathLength)),
    isParallel = false
)

fun newJcAliasRunnerFactory(
    generates: (JcInst) -> List<TaintAnalysisNode>,
    sanitizes: (JcExpr, TaintNode) -> Boolean,
    sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = BaseIfdsUnitRunnerFactory<JcMethod, JcInstLocation, JcInst>(
    JcAliasAnalyzerFactory(generates, sanitizes, sinks, maxPathLength)
)