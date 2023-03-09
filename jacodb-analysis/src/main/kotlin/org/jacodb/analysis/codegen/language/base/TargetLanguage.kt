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

package org.jacodb.analysis.codegen.language.base

import java.nio.file.Path
import org.jacodb.analysis.codegen.ast.base.presentation.callable.CallablePresentation
import org.jacodb.analysis.codegen.ast.base.presentation.callable.FunctionPresentation
import org.jacodb.analysis.codegen.ast.base.presentation.type.TypePresentation

interface TargetLanguage {
    enum class PredefinedPrimitives {
        INT,
        VOID
    }

    fun projectZipInResourcesName(): String
    fun resolveProjectPathToSources(projectPath: Path): Path
    fun getPredefinedType(name: String): TypePresentation?
    fun getPredefinedPrimitive(primitive: PredefinedPrimitives): TypePresentation?
    fun dumpType(type: TypePresentation, pathToSourcesDir: Path)
    fun dumpFunction(func: FunctionPresentation, pathToSourcesDir: Path)
    fun dumpStartFunction(name: String, func: FunctionPresentation, pathToSourcesDir: Path)
    fun dispatch(callable: CallablePresentation)
    fun unzipTemplateProject(pathToDirectoryWhereToUnzipTemplate: Path, fullClear: Boolean)
}