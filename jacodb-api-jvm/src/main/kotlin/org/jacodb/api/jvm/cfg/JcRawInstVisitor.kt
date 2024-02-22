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

interface JcRawInstVisitor<out T> {
    fun visitJcRawAssignInst(inst: JcRawAssignInst): T
    fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): T
    fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): T
    fun visitJcRawCallInst(inst: JcRawCallInst): T
    fun visitJcRawLabelInst(inst: JcRawLabelInst): T
    fun visitJcRawLineNumberInst(inst: JcRawLineNumberInst): T
    fun visitJcRawReturnInst(inst: JcRawReturnInst): T
    fun visitJcRawThrowInst(inst: JcRawThrowInst): T
    fun visitJcRawCatchInst(inst: JcRawCatchInst): T
    fun visitJcRawGotoInst(inst: JcRawGotoInst): T
    fun visitJcRawIfInst(inst: JcRawIfInst): T
    fun visitJcRawSwitchInst(inst: JcRawSwitchInst): T

    interface Default<out T> : JcRawInstVisitor<T> {
        fun defaultVisitJcRawInst(inst: JcRawInst): T

        override fun visitJcRawAssignInst(inst: JcRawAssignInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawCallInst(inst: JcRawCallInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawLabelInst(inst: JcRawLabelInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawLineNumberInst(inst: JcRawLineNumberInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawReturnInst(inst: JcRawReturnInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawThrowInst(inst: JcRawThrowInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawCatchInst(inst: JcRawCatchInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawGotoInst(inst: JcRawGotoInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawIfInst(inst: JcRawIfInst): T = defaultVisitJcRawInst(inst)
        override fun visitJcRawSwitchInst(inst: JcRawSwitchInst): T = defaultVisitJcRawInst(inst)
    }
}
