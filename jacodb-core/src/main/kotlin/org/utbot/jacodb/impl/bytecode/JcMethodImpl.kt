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

package org.utbot.jacodb.impl.bytecode

import org.objectweb.asm.tree.MethodNode
import org.utbot.jacodb.api.*
import org.utbot.jacodb.api.cfg.JcGraph
import org.utbot.jacodb.api.cfg.JcInst
import org.utbot.jacodb.api.cfg.JcInstList
import org.utbot.jacodb.api.cfg.JcRawInst
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.impl.cfg.JcGraphBuilder
import org.utbot.jacodb.impl.cfg.RawInstListBuilder
import org.utbot.jacodb.impl.fs.fullAsmNode
import org.utbot.jacodb.impl.types.MethodInfo
import org.utbot.jacodb.impl.types.TypeNameImpl
import org.utbot.jacodb.impl.types.signature.MethodResolutionImpl
import org.utbot.jacodb.impl.types.signature.MethodSignature

class JcMethodImpl(
    private val methodInfo: MethodInfo,
    private val source: ClassSource,
    override val enclosingClass: JcClassOrInterface
) : JcMethod {

    override val name: String get() = methodInfo.name
    override val access: Int get() = methodInfo.access
    override val signature: String? get() = methodInfo.signature
    override val returnType = TypeNameImpl(methodInfo.returnClass)

    override val exceptions: List<JcClassOrInterface> by lazy(LazyThreadSafetyMode.NONE) {
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

    override fun body(): MethodNode {
        return source.fullAsmNode.methods.first { it.name == name && it.desc == methodInfo.desc }
    }

    override val rawInstList: JcInstList<JcRawInst> by lazy {
        RawInstListBuilder(this, body().jsrInlined).build()
    }

    override fun flowGraph(): JcGraph {
        return JcGraphBuilder(this, rawInstList).buildFlowGraph()
    }

    override val instList: JcInstList<JcInst> by lazy {
        JcGraphBuilder(this, rawInstList).buildInstList()
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

}
