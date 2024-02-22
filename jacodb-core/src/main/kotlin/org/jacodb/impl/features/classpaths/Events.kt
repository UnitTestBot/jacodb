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

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedClassResult
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedTypeResult
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcGraph
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcRawInst

sealed class AbstractJcResolvedResult(val name: String) {

    class JcResolvedClassResultImpl(name: String, override val clazz: JcClassOrInterface?) :
        AbstractJcResolvedResult(name), JcResolvedClassResult

    class JcResolvedTypeResultImpl(name: String, override val type: JcType?) : AbstractJcResolvedResult(name),
        JcResolvedTypeResult
}

sealed class AbstractJcInstResult(val method: JcMethod) {

    class JcFlowGraphResultImpl(method: JcMethod, override val flowGraph: JcGraph) :
        AbstractJcInstResult(method), JcMethodExtFeature.JcFlowGraphResult

    class JcInstListResultImpl(method: JcMethod, override val instList: JcInstList<JcInst>) :
        AbstractJcInstResult(method), JcMethodExtFeature.JcInstListResult

    class JcRawInstListResultImpl(method: JcMethod, override val rawInstList: JcInstList<JcRawInst>) :
        AbstractJcInstResult(method), JcMethodExtFeature.JcRawInstListResult
}
