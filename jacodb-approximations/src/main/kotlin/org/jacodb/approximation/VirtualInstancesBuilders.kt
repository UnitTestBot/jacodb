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

import org.jacodb.api.JcAnnotation
import org.jacodb.api.TypeName
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.features.classpaths.virtual.VirtualClassesBuilder
import org.objectweb.asm.tree.MethodNode

class EnrichedVirtualMethodBuilder(
    name: String = "_enriched_virtual_"
) : VirtualClassesBuilder.VirtualMethodBuilder(name) {
    private var exceptions: List<TypeName> = emptyList()
    private var featuresChain: JcFeaturesChain = JcFeaturesChain(emptyList())
    private var asmNode: MethodNode = MethodNode()
    private var enrichedParameters: List<JcEnrichedVirtualParameter> = emptyList()
    private var annotations: List<JcAnnotation> = emptyList()

    fun exceptions(exceptions: List<TypeName>) = apply {
        this.exceptions = exceptions
    }

    fun featuresChain(featuresChain: JcFeaturesChain) = apply {
        this.featuresChain = featuresChain
    }

    fun asmNode(asmNode: MethodNode) = apply {
        this.asmNode = asmNode
    }

    fun annotations(annotations: List<JcAnnotation>) = apply {
        this.annotations = annotations
    }

    fun enrichedParameters(parameters: List<JcEnrichedVirtualParameter>) = apply {
        this.enrichedParameters = parameters
        this.params(*parameters.map { it.type.typeName }.toTypedArray())
    }

    override fun build(): JcEnrichedVirtualMethod {
        return JcEnrichedVirtualMethod(
            name,
            access,
            returnType,
            enrichedParameters,
            description,
            featuresChain,
            exceptions,
            asmNode,
            annotations
        )
    }
}

class EnrichedVirtualFieldBuilder(
    name: String = "_enriched_virtual_"
) : VirtualClassesBuilder.VirtualFieldBuilder(name) {
    private var annotations: List<JcAnnotation> = emptyList()

    fun annotations(annotations: List<JcAnnotation>) = apply {
        this.annotations = annotations
    }

    override fun build(): JcEnrichedVirtualField = JcEnrichedVirtualField(name, access, type, annotations)
}