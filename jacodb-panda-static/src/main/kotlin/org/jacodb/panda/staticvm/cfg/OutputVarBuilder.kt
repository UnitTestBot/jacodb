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

package org.jacodb.panda.staticvm.cfg

import org.jacodb.panda.staticvm.classpath.PandaMethod
import org.jacodb.panda.staticvm.classpath.PandaPrimitiveType
import org.jacodb.panda.staticvm.classpath.PandaType
import org.jacodb.panda.staticvm.ir.PandaAShlInstIr
import org.jacodb.panda.staticvm.ir.PandaAShrInstIr
import org.jacodb.panda.staticvm.ir.PandaAddInstIr
import org.jacodb.panda.staticvm.ir.PandaAndInstIr
import org.jacodb.panda.staticvm.ir.PandaBasicBlockIr
import org.jacodb.panda.staticvm.ir.PandaBitcastInstIr
import org.jacodb.panda.staticvm.ir.PandaBoundsCheckInstIr
import org.jacodb.panda.staticvm.ir.PandaBuiltinInstIr
import org.jacodb.panda.staticvm.ir.PandaCallDynamicInstIr
import org.jacodb.panda.staticvm.ir.PandaCallLaunchStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaCallLaunchVirtualInstIr
import org.jacodb.panda.staticvm.ir.PandaCallResolvedStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaCallResolvedVirtualInstIr
import org.jacodb.panda.staticvm.ir.PandaCallStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaCallVirtualInstIr
import org.jacodb.panda.staticvm.ir.PandaCastInstIr
import org.jacodb.panda.staticvm.ir.PandaCatchPhiInstIr
import org.jacodb.panda.staticvm.ir.PandaCheckCastInstIr
import org.jacodb.panda.staticvm.ir.PandaCmpInstIr
import org.jacodb.panda.staticvm.ir.PandaCompareInstIr
import org.jacodb.panda.staticvm.ir.PandaConstantInstIr
import org.jacodb.panda.staticvm.ir.PandaDivInstIr
import org.jacodb.panda.staticvm.ir.PandaFillConstArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaFunctionImmediateInstIr
import org.jacodb.panda.staticvm.ir.PandaHclassCheckInstIr
import org.jacodb.panda.staticvm.ir.PandaIfImmInstIr
import org.jacodb.panda.staticvm.ir.PandaInitClassInstIr
import org.jacodb.panda.staticvm.ir.PandaInstIr
import org.jacodb.panda.staticvm.ir.PandaInstIrVisitor
import org.jacodb.panda.staticvm.ir.PandaIntrinsicInstIr
import org.jacodb.panda.staticvm.ir.PandaIsInstanceInstIr
import org.jacodb.panda.staticvm.ir.PandaLenArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadAndInitClassInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadClassInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadFromConstantPoolInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadImmediateInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadObjFromConstInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadObjectDynamicInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadObjectInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadResolvedObjectFieldInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadResolvedObjectFieldStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadRuntimeClassInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadStringInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadTypeInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadUndefinedInstIr
import org.jacodb.panda.staticvm.ir.PandaModInstIr
import org.jacodb.panda.staticvm.ir.PandaMulInstIr
import org.jacodb.panda.staticvm.ir.PandaNegInstIr
import org.jacodb.panda.staticvm.ir.PandaNegativeCheckInstIr
import org.jacodb.panda.staticvm.ir.PandaNewArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaNewObjectInstIr
import org.jacodb.panda.staticvm.ir.PandaNotInstIr
import org.jacodb.panda.staticvm.ir.PandaNullCheckInstIr
import org.jacodb.panda.staticvm.ir.PandaNullPtrInstIr
import org.jacodb.panda.staticvm.ir.PandaOrInstIr
import org.jacodb.panda.staticvm.ir.PandaParameterInstIr
import org.jacodb.panda.staticvm.ir.PandaPhiInstIr
import org.jacodb.panda.staticvm.ir.PandaRefTypeCheckInstIr
import org.jacodb.panda.staticvm.ir.PandaResolveStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaResolveVirtualInstIr
import org.jacodb.panda.staticvm.ir.PandaReturnInstIr
import org.jacodb.panda.staticvm.ir.PandaReturnVoidInstIr
import org.jacodb.panda.staticvm.ir.PandaSafePointInstIr
import org.jacodb.panda.staticvm.ir.PandaSaveStateDeoptimizeInstIr
import org.jacodb.panda.staticvm.ir.PandaSaveStateInstIr
import org.jacodb.panda.staticvm.ir.PandaShlInstIr
import org.jacodb.panda.staticvm.ir.PandaShrInstIr
import org.jacodb.panda.staticvm.ir.PandaStoreArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaStoreObjectDynamicInstIr
import org.jacodb.panda.staticvm.ir.PandaStoreObjectInstIr
import org.jacodb.panda.staticvm.ir.PandaStoreResolvedObjectFieldInstIr
import org.jacodb.panda.staticvm.ir.PandaStoreResolvedObjectFieldStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaStoreStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaSubInstIr
import org.jacodb.panda.staticvm.ir.PandaThrowInstIr
import org.jacodb.panda.staticvm.ir.PandaTryInstIr
import org.jacodb.panda.staticvm.ir.PandaXorInstIr
import org.jacodb.panda.staticvm.ir.PandaZeroCheckInstIr

sealed interface LocalVarNode {
    val name: String
}

open class LeafVarNode(override val name: String, val type: PandaType) : LocalVarNode

class DependentVarNode(override val name: String, val bounds: List<String>) : LocalVarNode

class LoadArrayNode(override val name: String, val array: String) : LocalVarNode

class ThisNode(name: String, type: PandaType) : LeafVarNode(name, type)

class OutputVarBuilder(
    private val method: PandaMethod,
    private val block: PandaBasicBlockIr,
    private val handledType: PandaType? = null,
) : PandaInstIrVisitor<LocalVarNode?> {
    private val project = method.enclosingClass.project

    private fun default(inst: PandaInstIr): LeafVarNode {
        val type = project.findType(inst.type)
        return LeafVarNode(inst.id, type)
    }

    // TODO: default(inst)
    override fun visitPandaConstantInstIr(inst: PandaConstantInstIr): LocalVarNode {
        val type = project.findType(inst.type)
        return LeafVarNode(inst.id, type)
    }

    override fun visitPandaSafePointInstIr(inst: PandaSafePointInstIr): LocalVarNode? = null

    override fun visitPandaSaveStateInstIr(inst: PandaSaveStateInstIr): LocalVarNode? = null

    override fun visitPandaNewObjectInstIr(inst: PandaNewObjectInstIr): LocalVarNode {
        val classType = project.findClass(inst.objectClass).type
        return LeafVarNode(inst.id, classType)
    }

    override fun visitPandaNewArrayInstIr(inst: PandaNewArrayInstIr): LocalVarNode {
        val arrayType = project.findType(inst.arrayType)
        return LeafVarNode(inst.id, arrayType)
    }

    override fun visitPandaCallStaticInstIr(inst: PandaCallStaticInstIr): LocalVarNode {
        val returnType = project.findMethod(inst.method).returnType
        return LeafVarNode(inst.id, returnType)
    }

    override fun visitPandaCallLaunchStaticInstIr(inst: PandaCallLaunchStaticInstIr): LocalVarNode {
        val returnType = project.findMethod(inst.method).returnType
        return LeafVarNode(inst.id, returnType)
    }

    override fun visitPandaNullCheckInstIr(inst: PandaNullCheckInstIr): LocalVarNode {
        val bounds = inst.inputs.dropLast(1)
        return DependentVarNode(inst.id, bounds)
    }

    // TODO: default(inst)
    override fun visitPandaZeroCheckInstIr(inst: PandaZeroCheckInstIr): LocalVarNode {
        val type = project.findType(inst.type)
        return LeafVarNode(inst.id, type)
    }

    override fun visitPandaLoadStringInstIr(inst: PandaLoadStringInstIr): LocalVarNode {
        val stringType = project.stringClass.type
        return LeafVarNode(inst.id, stringType)
    }

    override fun visitPandaLoadTypeInstIr(inst: PandaLoadTypeInstIr): LocalVarNode {
        val typeType = project.typeClass.type
        return LeafVarNode(inst.id, typeType)
    }

    override fun visitPandaLoadRuntimeClassInstIr(inst: PandaLoadRuntimeClassInstIr): LocalVarNode? = null

    override fun visitPandaCallVirtualInstIr(inst: PandaCallVirtualInstIr): LocalVarNode {
        val returnType = project.findMethod(inst.method).returnType
        return LeafVarNode(inst.id, returnType)
    }

    override fun visitPandaCallLaunchVirtualInstIr(inst: PandaCallLaunchVirtualInstIr): LocalVarNode {
        val returnType = project.findMethod(inst.method).returnType
        return LeafVarNode(inst.id, returnType)
    }

    override fun visitPandaLoadAndInitClassInstIr(inst: PandaLoadAndInitClassInstIr): LocalVarNode? = null

    override fun visitPandaLoadClassInstIr(inst: PandaLoadClassInstIr): LocalVarNode? = null

    override fun visitPandaInitClassInstIr(inst: PandaInitClassInstIr): LocalVarNode? = null

    override fun visitPandaReturnVoidInstIr(inst: PandaReturnVoidInstIr): LocalVarNode? = null

    override fun visitPandaReturnInstIr(inst: PandaReturnInstIr): LocalVarNode? = null

    override fun visitPandaParameterInstIr(inst: PandaParameterInstIr): LocalVarNode =
        if (inst.index == 0 && !method.flags.isStatic) {
            ThisNode(inst.id, method.parameterTypes[inst.index])
        } else {
            LeafVarNode(inst.id, method.parameterTypes[inst.index])
        }

    override fun visitPandaLoadStaticInstIr(inst: PandaLoadStaticInstIr): LocalVarNode {
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        return LeafVarNode(inst.id, field.type)
    }

    override fun visitPandaLoadObjectInstIr(inst: PandaLoadObjectInstIr): LocalVarNode {
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        return LeafVarNode(inst.id, field.type)
    }

    override fun visitPandaStoreStaticInstIr(inst: PandaStoreStaticInstIr): LocalVarNode? = null

    override fun visitPandaStoreObjectInstIr(inst: PandaStoreObjectInstIr): LocalVarNode? = null

    override fun visitPandaLoadArrayInstIr(inst: PandaLoadArrayInstIr): LocalVarNode {
        val array = inst.inputs.first()
        return LoadArrayNode(inst.id, array)
    }

    override fun visitPandaStoreArrayInstIr(inst: PandaStoreArrayInstIr): LocalVarNode? = null

    override fun visitPandaCastInstIr(inst: PandaCastInstIr): LocalVarNode = default(inst)

    override fun visitPandaIsInstanceInstIr(inst: PandaIsInstanceInstIr): LocalVarNode =
        LeafVarNode(inst.id, PandaPrimitiveType.BOOL)

    override fun visitPandaCheckCastInstIr(inst: PandaCheckCastInstIr): LocalVarNode {
        val candidateType = project.findType(inst.candidateType)
        return LeafVarNode(inst.id, candidateType)
    }

    override fun visitPandaBitcastInstIr(inst: PandaBitcastInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaIfImmInstIr(inst: PandaIfImmInstIr): LocalVarNode? = null

    override fun visitPandaCompareInstIr(inst: PandaCompareInstIr): LocalVarNode = default(inst)

    override fun visitPandaPhiInstIr(inst: PandaPhiInstIr): LocalVarNode? =
        DependentVarNode(inst.id, inst.inputs).takeIf { inst.users.isNotEmpty() }

    override fun visitPandaAddInstIr(inst: PandaAddInstIr): LocalVarNode = default(inst)

    override fun visitPandaSubInstIr(inst: PandaSubInstIr): LocalVarNode = default(inst)

    override fun visitPandaMulInstIr(inst: PandaMulInstIr): LocalVarNode = default(inst)

    override fun visitPandaDivInstIr(inst: PandaDivInstIr): LocalVarNode = default(inst)

    override fun visitPandaModInstIr(inst: PandaModInstIr): LocalVarNode = default(inst)

    override fun visitPandaAndInstIr(inst: PandaAndInstIr): LocalVarNode = default(inst)

    override fun visitPandaOrInstIr(inst: PandaOrInstIr): LocalVarNode = default(inst)

    override fun visitPandaXorInstIr(inst: PandaXorInstIr): LocalVarNode = default(inst)

    override fun visitPandaShlInstIr(inst: PandaShlInstIr): LocalVarNode = default(inst)

    override fun visitPandaShrInstIr(inst: PandaShrInstIr): LocalVarNode = default(inst)

    override fun visitPandaAShlInstIr(inst: PandaAShlInstIr): LocalVarNode = default(inst)

    override fun visitPandaAShrInstIr(inst: PandaAShrInstIr): LocalVarNode = default(inst)

    override fun visitPandaCmpInstIr(inst: PandaCmpInstIr): LocalVarNode = default(inst)

    override fun visitPandaThrowInstIr(inst: PandaThrowInstIr): LocalVarNode? = null

    override fun visitPandaNegativeCheckInstIr(inst: PandaNegativeCheckInstIr): LocalVarNode = default(inst)

    override fun visitPandaSaveStateDeoptimizeInstIr(inst: PandaSaveStateDeoptimizeInstIr): LocalVarNode? = null

    override fun visitPandaNegInstIr(inst: PandaNegInstIr): LocalVarNode = default(inst)

    override fun visitPandaNotInstIr(inst: PandaNotInstIr): LocalVarNode = default(inst)

    override fun visitPandaLenArrayInstIr(inst: PandaLenArrayInstIr): LocalVarNode = default(inst)

    override fun visitPandaBoundsCheckInstIr(inst: PandaBoundsCheckInstIr): LocalVarNode = default(inst)

    override fun visitPandaNullPtrInstIr(inst: PandaNullPtrInstIr): LocalVarNode {
        val objectType = project.objectClass.type
        return LeafVarNode(inst.id, objectType)
    }

    override fun visitPandaLoadUndefinedInstIr(inst: PandaLoadUndefinedInstIr): LocalVarNode {
        val undefinedType = project.undefinedClass.type
        return LeafVarNode(inst.id, undefinedType)
    }

    override fun visitPandaRefTypeCheckInstIr(inst: PandaRefTypeCheckInstIr): LocalVarNode {
        val bounds = inst.inputs.take(1)
        return DependentVarNode(inst.id, bounds)
    }

    override fun visitPandaTryInstIr(inst: PandaTryInstIr): LocalVarNode? = null

    override fun visitPandaCatchPhiInstIr(inst: PandaCatchPhiInstIr): LocalVarNode? =
        if (inst.inputs.isNotEmpty()) {
            DependentVarNode(inst.id, inst.inputs).takeIf { inst.users.isNotEmpty() }
        } else {
            val throwableType = handledType ?: project.objectClass.type
            LeafVarNode(inst.id, throwableType)
        }

    override fun visitPandaIntrinsicInstIr(inst: PandaIntrinsicInstIr): LocalVarNode {
        val type = project.resolveIntrinsic(inst.intrinsicId)?.returnType ?: project.objectClass.type
        return LeafVarNode(inst.id, type)
    }

    override fun visitPandaLoadFromConstantPoolInstIr(inst: PandaLoadFromConstantPoolInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaResolveStaticInstIr(inst: PandaResolveStaticInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaResolveVirtualInstIr(inst: PandaResolveVirtualInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCallDynamicInstIr(inst: PandaCallDynamicInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCallResolvedVirtualInstIr(inst: PandaCallResolvedVirtualInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCallResolvedStaticInstIr(inst: PandaCallResolvedStaticInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaFillConstArrayInstIr(inst: PandaFillConstArrayInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaBuiltinInstIr(inst: PandaBuiltinInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLoadResolvedObjectFieldInstIr(inst: PandaLoadResolvedObjectFieldInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLoadResolvedObjectFieldStaticInstIr(inst: PandaLoadResolvedObjectFieldStaticInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStoreResolvedObjectFieldInstIr(inst: PandaStoreResolvedObjectFieldInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStoreResolvedObjectFieldStaticInstIr(inst: PandaStoreResolvedObjectFieldStaticInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLoadObjectDynamicInstIr(inst: PandaLoadObjectDynamicInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStoreObjectDynamicInstIr(inst: PandaStoreObjectDynamicInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaFunctionImmediateInstIr(inst: PandaFunctionImmediateInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaHclassCheckInstIr(inst: PandaHclassCheckInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLoadObjFromConstInstIr(inst: PandaLoadObjFromConstInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLoadImmediateInstIr(inst: PandaLoadImmediateInstIr): LocalVarNode? {
        TODO("Not yet implemented")
    }
}
