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

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcMethodExtFeature.*
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcGraph
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.features.classpaths.virtual.JcVirtualFieldImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

class JcEnrichedVirtualMethod(
    name: String,
    access: Int = Opcodes.ACC_PUBLIC,
    returnType: TypeName,
    parameters: List<JcEnrichedVirtualParameter>,
    description: String,
    private val featuresChain: JcFeaturesChain,
    override val exceptions: List<TypeName>,
    private val asmNode: MethodNode,
    override val annotations: List<JcAnnotation>,
) : JcVirtualMethodImpl(name, access, returnType, parameters, description) {

    override val rawInstList: JcInstList<JcRawInst>
        get() = featuresChain.call<JcMethodExtFeature, JcRawInstListResult> {
            it.rawInstList(this)
        }!!.rawInstList

    override val instList: JcInstList<JcInst>
        get() = featuresChain.call<JcMethodExtFeature, JcInstListResult> {
            it.instList(this)
        }!!.instList

    override fun asmNode(): MethodNode = asmNode

    override fun flowGraph(): JcGraph = featuresChain.call<JcMethodExtFeature, JcFlowGraphResult> {
        it.flowGraph(this)
    }!!.flowGraph

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcEnrichedVirtualMethod

        if (name != other.name) return false
        if (enclosingClass != other.enclosingClass) return false
        if (description != other.description) return false

        return true
    }

    override fun hashCode(): Int =
        name.hashCode() * 31 + enclosingClass.hashCode()

    override val signature: String?
        get() = null
}

class JcEnrichedVirtualParameter(
    index: Int,
    type: TypeName,
    override val name: String?,
    override val annotations: List<JcAnnotation>,
    override val access: Int,
) : JcVirtualParameter(index, type)

class JcEnrichedVirtualField(
    name: String,
    access: Int,
    type: TypeName,
    override val annotations: List<JcAnnotation>,
) : JcVirtualFieldImpl(name, access, type) {
    override val signature: String?
        get() = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcEnrichedVirtualField

        if (name != other.name) return false
        if (enclosingClass != other.enclosingClass) return false

        return true
    }

    override fun hashCode(): Int = name.hashCode() * 31 + enclosingClass.hashCode()
}
