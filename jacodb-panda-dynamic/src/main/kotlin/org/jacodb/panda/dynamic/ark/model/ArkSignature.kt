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

package org.jacodb.panda.dynamic.ark.model

import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.panda.dynamic.ark.base.ArkType

data class FileSignature(
    val projectName: String,
    val fileName: String,
) {
    override fun toString(): String {
        // Remove ".d.ts" and ".ts" file ext:
        val tmp = fileName.replace("""(\.d\.ts|\.ts)$""".toRegex(), "")
        return "@$projectName/$tmp"
    }
}

data class NamespaceSignature(
    val name: String,
    val namespace: NamespaceSignature? = null,
    val file: FileSignature? = null,
) {
    override fun toString(): String {
        if (namespace != null) {
            return "$namespace.$name"
        } else if (file != null) {
            return "$file: $name"
        } else {
            return name
        }
    }
}

data class ClassSignature(
    val name: String,
    val namespace: NamespaceSignature? = null,
    val file: FileSignature? = null,
) {
    override fun toString(): String {
        if (namespace != null) {
            return "$namespace::$name"
        } else if (file != null) {
            return "$file: $name"
        } else {
            return name
        }
    }
}

data class FieldSignature(
    val enclosingClass: ClassSignature,
    val sub: FieldSubSignature,
) {
    val name: String
        get() = sub.name

    val type: ArkType
        get() = sub.type

    override fun toString(): String {
        return "${enclosingClass.name}::$sub"
    }
}

data class FieldSubSignature(
    val name: String,
    val type: ArkType,
) {
    override fun toString(): String {
        return "$name: $type"
    }
}

data class MethodSignature(
    val enclosingClass: ClassSignature,
    val name: String,
    val parameters: List<ArkMethodParameter>,
    val returnType: ArkType,
) {

    constructor(
        enclosingClass: ClassSignature,
        sub: MethodSubSignature,
    ) : this(
        enclosingClass,
        sub.name,
        sub.parameters,
        sub.returnType,
    )

    override fun toString(): String {
        val params = parameters.joinToString()
        return "${enclosingClass.name}::$name($params): $returnType"
    }
}

data class MethodSubSignature(
    val name: String,
    val parameters: List<ArkMethodParameter>,
    val returnType: ArkType,
) {
    override fun toString(): String {
        val params = parameters.joinToString()
        return "$name($params): $returnType"
    }
}

data class ArkMethodParameter(
    val index: Int,
    val name: String,
    override val type: ArkType,
    val isOptional: Boolean = false,
) : CommonMethodParameter {
    override fun toString(): String {
        return "$name${if (isOptional) "?" else ""}: $type"
    }
}
