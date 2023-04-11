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

package org.jacodb.analysis.codegen.ast.impl

import org.jacodb.analysis.codegen.ast.base.expression.invocation.InvocationExpression
import org.jacodb.analysis.codegen.ast.base.presentation.callable.CallablePresentation
import org.jacodb.analysis.codegen.ast.base.sites.CallSite

class CallSiteImpl(
    // unique identifier with the function
    override val graphId: Int,
    override val parentCallable: CallablePresentation,
    override val invocationExpression: InvocationExpression,
    override var comments: ArrayList<String> = ArrayList()
    ) : SiteImpl(), CallSite {
    override fun equals(other: Any?): Boolean {
        if (other !is CallSite) {
            return false
        }

        if (parentCallable == other.parentCallable && graphId == other.graphId) {
            assert(other === this)
        }

        return other === this
    }

    override fun hashCode(): Int {
        return parentCallable.hashCode() * 31 + graphId
    }
}