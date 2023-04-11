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

package org.jacodb.analysis.codegen.language.impl

import org.jacodb.analysis.codegen.language.base.AnalysisVulnerabilityProvider
import org.jacodb.analysis.codegen.language.base.TargetLanguage
import org.jacodb.analysis.codegen.language.base.VulnerabilityInstance
import org.jacodb.analysis.codegen.CodeRepresentation
import org.jacodb.analysis.codegen.ast.base.*
import org.jacodb.analysis.codegen.ast.base.typeUsage.InstanceTypeUsage
import org.jacodb.analysis.codegen.ast.base.typeUsage.TypeUsage
import org.jacodb.analysis.codegen.ast.impl.*
import org.jacodb.analysis.codegen.dispatcherQueueName

class JavaNpeProvider : AnalysisVulnerabilityProvider {
    override fun provideInstance(codeRepresentation: CodeRepresentation): VulnerabilityInstance {
        return JavaNpeInstance(codeRepresentation)
    }

    override fun isApplicable(language: TargetLanguage): Boolean {
        return language is JavaLanguage
    }
}

class JavaNpeInstance(private val codeRepresentation: CodeRepresentation) : VulnerabilityInstance {
    companion object {
        private var vulnerabilitiesCounter = 0
    }

    private val id = "NpeInstance${++vulnerabilitiesCounter}"
    private val variableId = "variableFor$id"
    private val typeId = "TypeFor$id"
    private val startFunctionId = "startFunctionFor$id"
    private val arrayDeque = codeRepresentation.getPredefinedType("ArrayDeque<Integer>")!!

    private fun addPath(targetCall: Int) {
        val startFunction = codeRepresentation.getOrCreateStartFunction(startFunctionId)
        val dispatcher = startFunction.getLocalVariable(dispatcherQueueName)!!
        val dispatcherType = (dispatcher.usage as InstanceTypeUsage).typePresentation
        val dispatcherAddMethod = dispatcherType.getMethods("add").single()
        val preparationSite = startFunction.preparationSite
        val invocationExpression = MethodInvocationExpressionImpl(dispatcherAddMethod, dispatcher.reference)
        invocationExpression.addCommentsWithRemove(dispatcherAddMethod)
        invocationExpression.addCommentsWithRemove(dispatcher.reference)
        val dispatcherAddMethodParameter = dispatcherAddMethod.parameters.single()

        invocationExpression.addInCall(dispatcherAddMethodParameter, object : DirectStringSubstitution {
            override val substitution: String = targetCall.toString()
            override val evaluatedType: TypeUsage = dispatcherAddMethodParameter.usage
            override var comments: ArrayList<String> = ArrayList()
        })
        preparationSite.addBefore(invocationExpression)
    }

    override fun createSource(u: Int) {
        val startFunction = codeRepresentation.getOrCreateStartFunction(startFunctionId)
        val type = codeRepresentation.getOrCreateType(typeId)

        // must be initialized here as in following transits this will be parameter
        startFunction.createLocalVariable(dispatcherQueueName, arrayDeque.instanceType, arrayDeque.defaultValue)
        // initialized as null
        startFunction.createLocalVariable(variableId, type.instanceType)
        codeRepresentation.createDispatch(startFunction)
        transitVulnerability(startFunction.graphId, u)
    }

    override fun mutateVulnerability(u: Int, v: Int) {
        // TODO currently do not mutate, enhance by time
    }

    override fun transitVulnerability(u: Int, v: Int) {
        val functionU = codeRepresentation.getOrCreateFunctionFor(u)
        val functionV = codeRepresentation.getOrCreateFunctionFor(v)

        // as it can be either variable or parameter
        val dispatchArrayInU = functionU.getLocal(dispatcherQueueName)!!
        val variableInU = functionU.getLocal(variableId)!!

        // as it can be either variable or parameter
        val dispatchParameterInV = functionV.getOrCreateParameter(dispatcherQueueName, arrayDeque.instanceType)
        val parameterInV = functionV.getOrCreateParameter(variableId, variableInU.usage)

        val uvCallSite = functionU.getOrCreateCallSite(functionV)

        uvCallSite.invocationExpression.addInCall(dispatchParameterInV, dispatchArrayInU.reference)
        uvCallSite.invocationExpression.addInCall(parameterInV, variableInU.reference)
        addPath(v)
        codeRepresentation.createDispatch(functionV)
    }

    override fun createSink(v: Int) {
        val functionV = codeRepresentation.getOrCreateFunctionFor(v)
        val variableInV = functionV.getLocal(variableId)!!
        val vTerminationSite = functionV.terminationSite

        vTerminationSite.addDereference(variableInV.reference)
        addPath(-1)
    }
}