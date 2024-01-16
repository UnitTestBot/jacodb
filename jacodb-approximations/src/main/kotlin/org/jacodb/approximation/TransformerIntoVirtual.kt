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

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcProject
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.impl.features.JcFeaturesChain

object TransformerIntoVirtual {
    fun JcProject.transformMethodIntoVirtual(
        to: JcClassOrInterface,
        method: JcMethod
    ): JcEnrichedVirtualMethod = with(method) {
        val parameters = parameters.map { param ->
            // TODO process annotations somehow to eliminate approximations
            with(param) {
                JcEnrichedVirtualParameter(index, type.eliminateApproximation(), name, annotations, access)
            }
        }

        val featuresChain = features?.let { JcFeaturesChain(it) } ?: JcFeaturesChain(emptyList())

        val exceptions = exceptions.map { it.eliminateApproximation() }

        (EnrichedVirtualMethodBuilder()
            .name(name)
            .access(access)
            .returnType(returnType.eliminateApproximation().typeName) as EnrichedVirtualMethodBuilder)
            .enrichedParameters(parameters)
            .featuresChain(featuresChain)
            .exceptions(exceptions)
            .annotations(annotations)
            .asmNode(asmNode())
            .build()
            .also { it.bind(to) }
    }

    fun transformIntoVirtualField(
        to: JcClassOrInterface,
        field: JcField
    ): JcEnrichedVirtualField = with(field) {
        (EnrichedVirtualFieldBuilder()
            .name(name)
            .type(type.eliminateApproximation().typeName)
            .access(access) as EnrichedVirtualFieldBuilder)
            .annotations(annotations)
            .build()
            .also { it.bind(to) }
    }
}