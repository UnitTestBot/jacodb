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

package org.jacodb.analysis.codegen.language.impl

import org.jacodb.analysis.codegen.ast.base.InheritanceModifier
import org.jacodb.analysis.codegen.ast.base.VisibilityModifier
import org.jacodb.analysis.codegen.ast.base.presentation.callable.FunctionPresentation
import org.jacodb.analysis.codegen.ast.base.presentation.callable.local.LocalVariablePresentation
import org.jacodb.analysis.codegen.ast.base.presentation.type.TypePresentation
import org.jacodb.analysis.codegen.ast.base.sites.CallSite
import org.jacodb.analysis.codegen.ast.base.sites.Site
import org.jacodb.analysis.codegen.ast.base.sites.TerminationSite
import org.jacodb.analysis.codegen.ast.base.typeUsage.TypeUsage
import org.jacodb.analysis.codegen.ast.impl.MethodImpl

class FuncWrapper(
    func: FunctionPresentation,
    graphId: Int,
    containingType: TypePresentation,
    name: String,
    visibility: VisibilityModifier,
    returnType: TypeUsage,
    inheritanceModifier: InheritanceModifier,
    inheritedFrom: MethodImpl?,
    parameters: List<Pair<TypeUsage, String>>
) : MethodImpl(
    graphId, containingType,
    name,
    visibility, returnType, inheritanceModifier, inheritedFrom, parameters
) {
    private var func: FunctionPresentation

    init {
        this.func = func
    }

    override val callSites: MutableList<CallSite>
        get() = this.func.callSites.toMutableList()

    override val terminationSite: TerminationSite
        get() = this.func.terminationSite

    override val preparationSite: Site
        get() = this.func.preparationSite

    override val localVariables: Collection<LocalVariablePresentation>
        get() = this.func.localVariables

}