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

package org.jacodb.impl.bytecode

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcMethodExtFeature.JcFlowGraphResult
import org.jacodb.api.jvm.JcMethodExtFeature.JcInstListResult
import org.jacodb.api.jvm.JcMethodExtFeature.JcRawInstListResult
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcGraph
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.MethodInfo
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.TypeReference
import org.objectweb.asm.tree.MethodNode

class JcMethodImpl(
    private val methodInfo: MethodInfo,
    private val featuresChain: JcFeaturesChain,
    override val enclosingClass: JcClassOrInterface
) : JcMethod {

    override val name: String get() = methodInfo.name
    override val access: Int get() = methodInfo.access
    override val signature: String? get() = methodInfo.signature
    override val returnType: TypeName = TypeNameImpl(methodInfo.returnClass)

    override val exceptions: List<TypeName>
        get() {
            return methodInfo.exceptions.map { TypeNameImpl(it) }
        }

    override val declaration = JcDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    override val parameters: List<JcParameter>
        get() = methodInfo.parametersInfo.map { JcParameterImpl(this, it) }

    override val annotations: List<JcAnnotation>
        get() = methodInfo.annotations
            .filter { it.typeRef == null } // Type annotations are stored with method in bytecode, but they are not a part of method in language
            .map { JcAnnotationImpl(it, enclosingClass.classpath) }

    internal val returnTypeAnnotationInfos: List<AnnotationInfo>
        get() = methodInfo.annotations.filter {
            it.typeRef != null && TypeReference(it.typeRef).sort == TypeReference.METHOD_RETURN
        }

    internal fun parameterTypeAnnotationInfos(parameterIndex: Int): List<AnnotationInfo> =
        methodInfo.annotations.filter {
            it.typeRef != null && TypeReference(it.typeRef).sort == TypeReference.METHOD_FORMAL_PARAMETER
                && TypeReference(it.typeRef).formalParameterIndex == parameterIndex
        }

    override val description get() = methodInfo.desc

    override fun <T> withAsmNode(body: (MethodNode) -> T): T {
        val methodNode = enclosingClass.withAsmNode { classNode ->
            classNode.methods.first { it.name == name && it.desc == methodInfo.desc }
        }

        return synchronized(methodNode) {
            body(methodNode.jsrInlined)
        }
    }

    override val rawInstList: JcInstList<JcRawInst>
        get() {
            return featuresChain.call<JcMethodExtFeature, JcRawInstListResult> { it.rawInstList(this) }!!.rawInstList
        }

    override fun flowGraph(): JcGraph {
        return featuresChain.call<JcMethodExtFeature, JcFlowGraphResult> { it.flowGraph(this) }!!.flowGraph
    }


    override val instList: JcInstList<JcInst> get() {
        return featuresChain.call<JcMethodExtFeature, JcInstListResult> { it.instList(this) }!!.instList
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcMethodImpl) {
            return false
        }
        return other.name == name && enclosingClass == other.enclosingClass && methodInfo.desc == other.methodInfo.desc
    }

    override fun hashCode(): Int {
        return 31 * enclosingClass.hashCode() + name.hashCode()
    }

    override fun toString(): String {
        return "${enclosingClass}#$name(${parameters.joinToString { it.type.typeName }})"
    }

}
