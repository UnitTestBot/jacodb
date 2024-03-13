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
    fun visitPandaConstantInstInfo(inst: PandaConstantInstIr): T
    fun visitPandaSafePointInstInfo(inst: PandaSafePointInstIr): T
    fun visitPandaSaveStateInstInfo(inst: PandaSaveStateInstIr): T
    fun visitPandaNewObjectInstInfo(inst: PandaNewObjectInstIr): T
    fun visitPandaNewArrayInstInfo(inst: PandaNewArrayInstIr): T
    fun visitPandaCallStaticInstInfo(inst: PandaCallStaticInstIr): T
    fun visitPandaNullCheckInstInfo(inst: PandaNullCheckInstIr): T
    fun visitPandaZeroCheckInstInfo(inst: PandaZeroCheckInstIr): T
    fun visitPandaLoadStringInstInfo(inst: PandaLoadStringInstIr): T
    fun visitPandaCallVirtualInstInfo(inst: PandaCallVirtualInstIr): T
    fun visitPandaLoadAndInitClassInstInfo(inst: PandaLoadAndInitClassInstIr): T
    fun visitPandaLoadClassInstInfo(inst: PandaLoadClassInstIr): T
    fun visitPandaInitClassInstInfo(inst: PandaInitClassInstIr): T
    fun visitPandaReturnVoidInstInfo(inst: PandaReturnVoidInstIr): T
    fun visitPandaReturnInstInfo(inst: PandaReturnInstIr): T
    fun visitPandaParameterInstInfo(inst: PandaParameterInstIr): T
    fun visitPandaLoadStaticInstInfo(inst: PandaLoadStaticInstIr): T
    fun visitPandaLoadObjectInstInfo(inst: PandaLoadObjectInstIr): T
    fun visitPandaStoreStaticInstInfo(inst: PandaStoreStaticInstIr): T
    fun visitPandaStoreObjectInstInfo(inst: PandaStoreObjectInstIr): T
    fun visitPandaLoadArrayInstInfo(inst: PandaLoadArrayInstIr): T
    fun visitPandaStoreArrayInstInfo(inst: PandaStoreArrayInstIr): T
    fun visitPandaCastInstInfo(inst: PandaCastInstIr): T
    fun visitPandaIsInstanceInstInfo(inst: PandaIsInstanceInstIr): T
    fun visitPandaCheckCastInstInfo(inst: PandaCheckCastInstIr): T
    fun visitPandaIfImmInstInfo(inst: PandaIfImmInstIr): T
    fun visitPandaCompareInstInfo(inst: PandaCompareInstIr): T
    fun visitPandaPhiInstInfo(inst: PandaPhiInstIr): T
    fun visitPandaAddInstInfo(inst: PandaAddInstIr): T
    fun visitPandaSubInstInfo(inst: PandaSubInstIr): T
    fun visitPandaMulInstInfo(inst: PandaMulInstIr): T
    fun visitPandaDivInstInfo(inst: PandaDivInstIr): T
    fun visitPandaModInstInfo(inst: PandaModInstIr): T
    fun visitPandaAndInstInfo(inst: PandaAndInstIr): T
    fun visitPandaOrInstInfo(inst: PandaOrInstIr): T
    fun visitPandaXorInstInfo(inst: PandaXorInstIr): T
    fun visitPandaShlInstInfo(inst: PandaShlInstIr): T
    fun visitPandaShrInstInfo(inst: PandaShrInstIr): T
    fun visitPandaAShlInstInfo(inst: PandaAShlInstIr): T
    fun visitPandaAShrInstInfo(inst: PandaAShrInstIr): T
    fun visitPandaCmpInstInfo(inst: PandaCmpInstIr): T
    fun visitPandaThrowInstInfo(inst: PandaThrowInstIr): T
    fun visitPandaNegativeCheckInstInfo(inst: PandaNegativeCheckInstIr): T
    fun visitPandaSaveStateDeoptimizeInstInfo(inst: PandaSaveStateDeoptimizeInstIr): T
    fun visitPandaNegInstInfo(inst: PandaNegInstIr): T
    fun visitPandaNotInstInfo(inst: PandaNotInstIr): T
    fun visitPandaLenArrayInstInfo(inst: PandaLenArrayInstIr): T
    fun visitPandaBoundsCheckInstInfo(inst: PandaBoundsCheckInstIr): T
    fun visitPandaNullPtrInstInfo(inst: PandaNullPtrInstIr): T
    fun visitPandaLoadUndefinedInstInfo(inst: PandaLoadUndefinedInstIr): T
    fun visitPandaRefTypeCheckInstInfo(inst: PandaRefTypeCheckInstIr): T
    fun visitPandaTryInstInfo(inst: PandaTryInstIr): T
    fun visitPandaCatchPhiInstInfo(inst: PandaCatchPhiInstIr): T
}