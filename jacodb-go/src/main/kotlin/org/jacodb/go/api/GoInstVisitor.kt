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

package org.jacodb.go.api

interface GoInstVisitor<T> {
    fun visitGoJumpInst(inst: GoJumpInst): T
    fun visitGoIfInst(inst: GoIfInst): T
    fun visitGoReturnInst(inst: GoReturnInst): T
    fun visitGoRunDefersInst(inst: GoRunDefersInst): T
    fun visitGoPanicInst(inst: GoPanicInst): T
    fun visitGoGoInst(inst: GoGoInst): T
    fun visitGoDeferInst(inst: GoDeferInst): T
    fun visitGoSendInst(inst: GoSendInst): T
    fun visitGoStoreInst(inst: GoStoreInst): T
    fun visitGoMapUpdateInst(inst: GoMapUpdateInst): T
    fun visitGoDebugRefInst(inst: GoDebugRefInst): T
    fun visitExternalGoInst(inst: GoInst): T
    fun visitGoCallInst(inst: GoCallInst): T
    fun visitGoAssignInst(inst: GoAssignInst): T
}
