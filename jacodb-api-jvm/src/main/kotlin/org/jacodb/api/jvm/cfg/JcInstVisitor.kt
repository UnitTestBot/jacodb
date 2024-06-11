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

package org.jacodb.api.jvm.cfg

interface JcInstVisitor<out T> {
    fun visitExternalJcInst(inst: JcInst): T

    fun visitJcAssignInst(inst: JcAssignInst): T
    fun visitJcEnterMonitorInst(inst: JcEnterMonitorInst): T
    fun visitJcExitMonitorInst(inst: JcExitMonitorInst): T
    fun visitJcCallInst(inst: JcCallInst): T
    fun visitJcReturnInst(inst: JcReturnInst): T
    fun visitJcThrowInst(inst: JcThrowInst): T
    fun visitJcCatchInst(inst: JcCatchInst): T
    fun visitJcGotoInst(inst: JcGotoInst): T
    fun visitJcIfInst(inst: JcIfInst): T
    fun visitJcSwitchInst(inst: JcSwitchInst): T

    interface Default<out T> : JcInstVisitor<T> {
        fun defaultVisitJcInst(inst: JcInst): T

        override fun visitExternalJcInst(inst: JcInst): T = defaultVisitJcInst(inst)

        override fun visitJcAssignInst(inst: JcAssignInst): T = defaultVisitJcInst(inst)
        override fun visitJcEnterMonitorInst(inst: JcEnterMonitorInst): T = defaultVisitJcInst(inst)
        override fun visitJcExitMonitorInst(inst: JcExitMonitorInst): T = defaultVisitJcInst(inst)
        override fun visitJcCallInst(inst: JcCallInst): T = defaultVisitJcInst(inst)
        override fun visitJcReturnInst(inst: JcReturnInst): T = defaultVisitJcInst(inst)
        override fun visitJcThrowInst(inst: JcThrowInst): T = defaultVisitJcInst(inst)
        override fun visitJcCatchInst(inst: JcCatchInst): T = defaultVisitJcInst(inst)
        override fun visitJcGotoInst(inst: JcGotoInst): T = defaultVisitJcInst(inst)
        override fun visitJcIfInst(inst: JcIfInst): T = defaultVisitJcInst(inst)
        override fun visitJcSwitchInst(inst: JcSwitchInst): T = defaultVisitJcInst(inst)
    }
}
