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

package org.jacodb.impl.features.classpaths

import org.jacodb.api.JcFeatureEvent
import org.jacodb.api.JcInstExtFeature
import org.jacodb.api.JcMethod
import org.jacodb.api.JcMethodExtFeature
import org.jacodb.api.JcMethodExtFeature.JcInstListResult
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.impl.cfg.JcGraphImpl
import org.jacodb.impl.cfg.JcInstListBuilder
import org.jacodb.impl.cfg.RawInstListBuilder
import org.jacodb.impl.features.JcFeatureEventImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult.JcFlowGraphResultImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult.JcInstListResultImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult.JcRawInstListResultImpl

class MethodInstructionsFeature(
    private val keepLocalVariableNames: Boolean
) : JcMethodExtFeature {

    private val JcMethod.methodFeatures
        get() = enclosingClass.classpath.features?.filterIsInstance<JcInstExtFeature>().orEmpty()


    override fun flowGraph(method: JcMethod): JcMethodExtFeature.JcFlowGraphResult {
        return JcFlowGraphResultImpl(method, JcGraphImpl(method, method.instList.instructions))
    }

    override fun instList(method: JcMethod): JcInstListResult {
        val list: JcInstList<JcInst> = JcInstListBuilder(method, method.rawInstList).buildInstList()
        return JcInstListResultImpl(method, method.methodFeatures.fold(list) { value, feature ->
            feature.transformInstList(method, value)
        })
    }

    override fun rawInstList(method: JcMethod): JcMethodExtFeature.JcRawInstListResult {
        val list: JcInstList<JcRawInst> = RawInstListBuilder(method, method.asmNode(), keepLocalVariableNames).build()
        return JcRawInstListResultImpl(method, method.methodFeatures.fold(list) { value, feature ->
            feature.transformRawInstList(method, value)
        })
    }

    override fun event(result: Any): JcFeatureEvent {
        return JcFeatureEventImpl(this, result)
    }

}