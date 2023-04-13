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

import org.jacodb.api.ClassSource
import org.jacodb.api.JcAnnotation
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspathFeature
import org.jacodb.api.JcInstExtFeature
import org.jacodb.api.JcMethod
import org.jacodb.api.JcParameter
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.api.ext.findClass
import org.jacodb.impl.cfg.JcGraphBuilder
import org.jacodb.impl.cfg.RawInstListBuilder
import org.jacodb.impl.fs.fullAsmNode
import org.jacodb.impl.softLazy
import org.jacodb.impl.types.MethodInfo
import org.jacodb.impl.types.TypeNameImpl
import org.jacodb.impl.types.signature.MethodResolutionImpl
import org.jacodb.impl.types.signature.MethodSignature
import org.objectweb.asm.tree.MethodNode
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JcMethodImpl(
    private val methodInfo: MethodInfo,
    private val source: ClassSource,
    private val features: List<JcClasspathFeature>?,
    override val enclosingClass: JcClassOrInterface
) : JcMethod {

    override val name: String get() = methodInfo.name
    override val access: Int get() = methodInfo.access
    override val signature: String? get() = methodInfo.signature
    override val returnType = TypeNameImpl(methodInfo.returnClass)

    internal val methodFeatures = features?.filterIsInstance<JcInstExtFeature>()
    private val instructions = JcGraphHolder(this)

    override val exceptions: List<JcClassOrInterface> by lazy(PUBLICATION) {
        val methodSignature = MethodSignature.of(this)
        if (methodSignature is MethodResolutionImpl) {
            methodSignature.exceptionTypes.map {
                enclosingClass.classpath.findClass(it.name)
            }
        } else {
            emptyList()
        }
    }

    override val declaration = JcDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    override val parameters: List<JcParameter>
        get() = methodInfo.parametersInfo.map { JcParameterImpl(this, it) }

    override val annotations: List<JcAnnotation>
        get() = methodInfo.annotations.map { JcAnnotationImpl(it, enclosingClass.classpath) }

    override val description get() = methodInfo.desc

    override fun asmNode(): MethodNode {
        return source.fullAsmNode.methods.first { it.name == name && it.desc == methodInfo.desc }
    }

    override val rawInstList: JcInstList<JcRawInst> get() = instructions.rawInstList

    override fun flowGraph() = instructions.flowGraph

    override val instList: JcInstList<JcInst> get() = instructions.instList

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

private class JcGraphHolder(val method: JcMethodImpl) {

    val rawInstList: JcInstList<JcRawInst> by softLazy {
        val list: JcInstList<JcRawInst> = RawInstListBuilder(method, method.asmNode().jsrInlined).build()
        method.methodFeatures?.fold(list) { value, feature ->
            feature.transformRawInstList(method, value)
        } ?: list
    }

    val flowGraph by softLazy {
        JcGraphBuilder(method, rawInstList).buildFlowGraph()
    }

    val instList: JcInstList<JcInst> by softLazy {
        val list: JcInstList<JcInst> = JcGraphBuilder(method, rawInstList).buildInstList()
        method.methodFeatures?.fold(list) { value, feature ->
            feature.transformInstList(method, value)
        } ?: list
    }

}
