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

package org.jacodb.panda.staticvm.ir

interface PandaInstIrVisitor<T> {
    fun visitPandaConstantInstIr(inst: PandaConstantInstIr): T
    fun visitPandaSafePointInstIr(inst: PandaSafePointInstIr): T
    fun visitPandaSaveStateInstIr(inst: PandaSaveStateInstIr): T
    fun visitPandaNewObjectInstIr(inst: PandaNewObjectInstIr): T
    fun visitPandaNewArrayInstIr(inst: PandaNewArrayInstIr): T
    fun visitPandaCallStaticInstIr(inst: PandaCallStaticInstIr): T
    fun visitPandaNullCheckInstIr(inst: PandaNullCheckInstIr): T
    fun visitPandaZeroCheckInstIr(inst: PandaZeroCheckInstIr): T
    fun visitPandaLoadStringInstIr(inst: PandaLoadStringInstIr): T
    fun visitPandaLoadTypeInstIr(inst: PandaLoadTypeInstIr): T
    fun visitPandaLoadRuntimeClassInstIr(inst: PandaLoadRuntimeClassInstIr): T
    fun visitPandaCallVirtualInstIr(inst: PandaCallVirtualInstIr): T
    fun visitPandaCallLaunchVirtualInstIr(inst: PandaCallLaunchVirtualInstIr): T
    fun visitPandaCallLaunchStaticInstIr(inst: PandaCallLaunchStaticInstIr): T
    fun visitPandaLoadAndInitClassInstIr(inst: PandaLoadAndInitClassInstIr): T
    fun visitPandaLoadClassInstIr(inst: PandaLoadClassInstIr): T
    fun visitPandaInitClassInstIr(inst: PandaInitClassInstIr): T
    fun visitPandaReturnVoidInstIr(inst: PandaReturnVoidInstIr): T
    fun visitPandaReturnInstIr(inst: PandaReturnInstIr): T
    fun visitPandaParameterInstIr(inst: PandaParameterInstIr): T
    fun visitPandaLoadStaticInstIr(inst: PandaLoadStaticInstIr): T
    fun visitPandaLoadObjectInstIr(inst: PandaLoadObjectInstIr): T
    fun visitPandaStoreStaticInstIr(inst: PandaStoreStaticInstIr): T
    fun visitPandaStoreObjectInstIr(inst: PandaStoreObjectInstIr): T
    fun visitPandaLoadArrayInstIr(inst: PandaLoadArrayInstIr): T
    fun visitPandaStoreArrayInstIr(inst: PandaStoreArrayInstIr): T
    fun visitPandaIsInstanceInstIr(inst: PandaIsInstanceInstIr): T
    fun visitPandaCheckCastInstIr(inst: PandaCheckCastInstIr): T
    fun visitPandaBitcastInstIr(inst: PandaBitcastInstIr): T
    fun visitPandaCastInstIr(inst: PandaCastInstIr): T
    fun visitPandaIfImmInstIr(inst: PandaIfImmInstIr): T
    fun visitPandaCompareInstIr(inst: PandaCompareInstIr): T
    fun visitPandaPhiInstIr(inst: PandaPhiInstIr): T
    fun visitPandaAddInstIr(inst: PandaAddInstIr): T
    fun visitPandaSubInstIr(inst: PandaSubInstIr): T
    fun visitPandaMulInstIr(inst: PandaMulInstIr): T
    fun visitPandaDivInstIr(inst: PandaDivInstIr): T
    fun visitPandaModInstIr(inst: PandaModInstIr): T
    fun visitPandaAndInstIr(inst: PandaAndInstIr): T
    fun visitPandaOrInstIr(inst: PandaOrInstIr): T
    fun visitPandaXorInstIr(inst: PandaXorInstIr): T
    fun visitPandaShlInstIr(inst: PandaShlInstIr): T
    fun visitPandaShrInstIr(inst: PandaShrInstIr): T
    fun visitPandaAShlInstIr(inst: PandaAShlInstIr): T
    fun visitPandaAShrInstIr(inst: PandaAShrInstIr): T
    fun visitPandaCmpInstIr(inst: PandaCmpInstIr): T
    fun visitPandaThrowInstIr(inst: PandaThrowInstIr): T
    fun visitPandaNegativeCheckInstIr(inst: PandaNegativeCheckInstIr): T
    fun visitPandaSaveStateDeoptimizeInstIr(inst: PandaSaveStateDeoptimizeInstIr): T
    fun visitPandaNegInstIr(inst: PandaNegInstIr): T
    fun visitPandaNotInstIr(inst: PandaNotInstIr): T
    fun visitPandaLenArrayInstIr(inst: PandaLenArrayInstIr): T
    fun visitPandaBoundsCheckInstIr(inst: PandaBoundsCheckInstIr): T
    fun visitPandaNullPtrInstIr(inst: PandaNullPtrInstIr): T
    fun visitPandaLoadUndefinedInstIr(inst: PandaLoadUndefinedInstIr): T
    fun visitPandaRefTypeCheckInstIr(inst: PandaRefTypeCheckInstIr): T
    fun visitPandaTryInstIr(inst: PandaTryInstIr): T
    fun visitPandaCatchPhiInstIr(inst: PandaCatchPhiInstIr): T
    fun visitPandaIntrinsicInstIr(inst: PandaIntrinsicInstIr): T
    fun visitPandaLoadFromConstantPoolInstIr(inst: PandaLoadFromConstantPoolInstIr): T
    fun visitPandaResolveStaticInstIr(inst: PandaResolveStaticInstIr): T
    fun visitPandaResolveVirtualInstIr(inst: PandaResolveVirtualInstIr): T
    fun visitPandaCallDynamicInstIr(inst: PandaCallDynamicInstIr): T
    fun visitPandaCallResolvedVirtualInstIr(inst: PandaCallResolvedVirtualInstIr): T
    fun visitPandaCallResolvedStaticInstIr(inst: PandaCallResolvedStaticInstIr): T
    fun visitPandaFillConstArrayInstIr(inst: PandaFillConstArrayInstIr): T
    fun visitPandaBuiltinInstIr(inst: PandaBuiltinInstIr): T
    fun visitPandaLoadResolvedObjectFieldInstIr(inst: PandaLoadResolvedObjectFieldInstIr): T
    fun visitPandaLoadResolvedObjectFieldStaticInstIr(inst: PandaLoadResolvedObjectFieldStaticInstIr): T
    fun visitPandaStoreResolvedObjectFieldInstIr(inst: PandaStoreResolvedObjectFieldInstIr): T
    fun visitPandaStoreResolvedObjectFieldStaticInstIr(inst: PandaStoreResolvedObjectFieldStaticInstIr): T
    fun visitPandaLoadObjectDynamicInstIr(inst: PandaLoadObjectDynamicInstIr): T
    fun visitPandaStoreObjectDynamicInstIr(inst: PandaStoreObjectDynamicInstIr): T
    fun visitPandaFunctionImmediateInstIr(inst: PandaFunctionImmediateInstIr): T
    fun visitPandaHclassCheckInstIr(inst: PandaHclassCheckInstIr): T
    fun visitPandaLoadObjFromConstInstIr(inst: PandaLoadObjFromConstInstIr): T
    fun visitPandaLoadImmediateInstIr(inst: PandaLoadImmediateInstIr): T
}
