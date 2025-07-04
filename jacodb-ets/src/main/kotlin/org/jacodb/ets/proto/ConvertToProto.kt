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

package org.jacodb.ets.proto

import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsModifiers
import org.jacodb.ets.model.EtsNamespace
import org.jacodb.ets.model.EtsNamespaceSignature
import org.jacodb.ets.model.EtsScene
import model.Block as ProtoBlock
import model.BlockCfg as ProtoBlockCfg
import model.Class as ProtoClass
import model.ClassSignature as ProtoClassSignature
import model.Field as ProtoField
import model.FieldSignature as ProtoFieldSignature
import model.File as ProtoFile
import model.FileSignature as ProtoFileSignature
import model.Method as ProtoMethod
import model.MethodParameter as ProtoMethodParameter
import model.MethodSignature as ProtoMethodSignature
import model.Namespace as ProtoNamespace
import model.NamespaceSignature as ProtoNamespaceSignature
import model.Scene as ProtoScene

fun EtsScene.toProto(): ProtoScene {
    return ProtoScene(
        files = projectFiles.map { it.toProto() },
        sdkFiles = sdkFiles.map { it.toProto() },
    )
}

fun EtsFile.toProto(): ProtoFile {
    return ProtoFile(
        signature = signature.toProto(),
        classes = classes.map { it.toProto() },
        namespaces = namespaces.map { it.toProto() },
    )
}

fun EtsNamespace.toProto(): ProtoNamespace {
    return ProtoNamespace(
        signature = signature.toProto(),
        classes = classes.map { it.toProto() },
        namespaces = namespaces.map { it.toProto() },
    )
}

fun EtsClass.toProto(): ProtoClass {
    return ProtoClass(
        signature = signature.toProto(),
        type_parameters = typeParameters.map { it.toProto() },
        fields = fields.map { it.toProto() },
        methods = methods.map { it.toProto() },
    )
}

fun EtsField.toProto(): ProtoField {
    check(this is EtsFieldImpl)
    return ProtoField(
        signature = signature.toProto(),
        modifiers = modifiers.toProto(),
        is_optional = isOptional,
        is_definitely_assigned = isDefinitelyAssigned,
    )
}

fun EtsMethod.toProto(): ProtoMethod {
    return ProtoMethod(
        signature = signature.toProto(),
        modifiers = modifiers.toProto(),
        cfg = cfg.toProto(),
    )
}

fun EtsFileSignature.toProto(): ProtoFileSignature {
    return ProtoFileSignature(
        project_name = projectName,
        file_name = fileName,
    )
}

fun EtsNamespaceSignature.toProto(): ProtoNamespaceSignature {
    return ProtoNamespaceSignature(
        name = name,
        file_ = file.toProto(),
        parent = namespace?.toProto(),
    )
}

fun EtsClassSignature.toProto(): ProtoClassSignature {
    return ProtoClassSignature(
        name = name,
        file_ = file.toProto(),
        namespace = namespace?.toProto(),
    )
}

fun EtsMethodSignature.toProto(): ProtoMethodSignature {
    return ProtoMethodSignature(
        name = name,
        enclosing_class = enclosingClass.toProto(),
        parameters = parameters.map { it.toProto() },
        returnType = returnType.toProto(),
    )
}

fun EtsMethodParameter.toProto(): ProtoMethodParameter {
    return ProtoMethodParameter(
        name = name,
        type = type.toProto(),
        isOptional = isOptional,
        isRest = isRest,
    )
}

fun EtsFieldSignature.toProto(): ProtoFieldSignature {
    return ProtoFieldSignature(
        name = name,
        enclosing_class = enclosingClass.toProto(),
        type = type.toProto(),
    )
}

fun EtsModifiers.toProto(): Int {
    return mask
}

fun EtsBlockCfg.toProto(): ProtoBlockCfg {
    return ProtoBlockCfg(
        blocks = blocks.map { block ->
            ProtoBlock(
                id = block.id,
                statements = block.statements.map { stmt -> stmt.toProto() },
                successors = successors.getValue(block.id),
                // Note: predecessors are omitted
            )
        },
    )
}
