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

package org.jacodb.approximation

import org.jacodb.api.JcClassExtFeature
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcField
import org.jacodb.api.JcInstExtFeature
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.approximation.ApproximationsMappingFeature.findApproximationByOriginOrNull
import org.jacodb.approximation.TransformerIntoVirtual.transformIntoVirtualField
import org.jacodb.approximation.TransformerIntoVirtual.transformMethodIntoVirtual
import org.jacodb.impl.cfg.JcInstListImpl

/**
 * A feature allowing to retrieve fields and methods from an approximation for a specified class.
 */
object ClassContentApproximationFeature : JcClassExtFeature {
    /**
     * Returns a list of [JcEnrichedVirtualField] if there is an approximation for [clazz] and null otherwise.
     */
    override fun fieldsOf(clazz: JcClassOrInterface): List<JcField>? {
        val approximationName = findApproximationByOriginOrNull(clazz.name.toOriginalName()) ?: return null
        val approximationClass = clazz.classpath.findClassOrNull(approximationName) ?: return null

        return approximationClass.declaredFields.map { transformIntoVirtualField(clazz, it) }
    }

    /**
     * Returns a list of [JcEnrichedVirtualMethod] if there is an approximation for [clazz] and null otherwise.
     */
    override fun methodsOf(clazz: JcClassOrInterface): List<JcMethod>? {
        val approximationName = findApproximationByOriginOrNull(clazz.name.toOriginalName()) ?: return null
        val approximationClass = clazz.classpath.findClassOrNull(approximationName) ?: return null

        return approximationClass.declaredMethods.map {
            approximationClass.classpath.transformMethodIntoVirtual(clazz, it)
        }
    }
}

/**
 * A feature replacing all occurrences of approximations classes names with their targets names.
 */
object ApproximationsInstructionsFeature : JcInstExtFeature {
    override fun transformRawInstList(method: JcMethod, list: JcInstList<JcRawInst>): JcInstList<JcRawInst> {
        return JcInstListImpl(list.map { it.accept(InstSubstitutorForApproximations) })
    }
}