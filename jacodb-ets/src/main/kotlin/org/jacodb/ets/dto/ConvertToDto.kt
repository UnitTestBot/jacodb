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

package org.jacodb.ets.dto

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespaceSignature

fun EtsFileSignature.toDto(): FileSignatureDto =
    FileSignatureDto(
        projectName = this.projectName,
        fileName = this.fileName,
    )

fun EtsNamespaceSignature.toDto(): NamespaceSignatureDto =
    NamespaceSignatureDto(
        name = this.name,
        declaringFile = this.file.toDto(),
        declaringNamespace = this.namespace?.toDto(),
    )

fun EtsClassSignature.toDto(): ClassSignatureDto =
    ClassSignatureDto(
        name = this.name,
        declaringFile = this.file.toDto(),
        declaringNamespace = this.namespace?.toDto(),
    )

fun EtsFieldSignature.toDto(): FieldSignatureDto =
    FieldSignatureDto(
        declaringClass = this.enclosingClass.toDto(),
        name = this.name,
        type = this.type.toDto(),
    )

fun EtsMethodSignature.toDto(): MethodSignatureDto =
    MethodSignatureDto(
        declaringClass = this.enclosingClass.toDto(),
        name = this.name,
        parameters = this.parameters.map { it.toDto() },
        returnType = this.returnType.toDto(),
    )

fun EtsMethodParameter.toDto(): MethodParameterDto =
    MethodParameterDto(
        name = this.name,
        type = this.type.toDto(),
        isOptional = this.isOptional,
    )
