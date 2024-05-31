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

package org.jacodb.panda.dynamic.ark

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
    val sub: FieldSubSignature,
    val enclosingClass: ClassSignature,
) {
    override fun toString(): String {
        return "${enclosingClass.name}::$sub"
    }
}

data class FieldSubSignature(
    val name: String,
    val type: Type,
    val isOptional: Boolean,
) {
    override fun toString(): String {
        return "$name${if (isOptional) "?" else ""}: $type"
    }
}

data class MethodSignature(
    val sub: MethodSubSignature,
    val enclosingClass: ClassSignature,
) {
    override fun toString(): String {
        return "${enclosingClass.name}::$sub"
    }
}

data class MethodSubSignature(
    val name: String,
    val parameters: List<MethodParameter>,
    val returnType: Type,
) {
    override fun toString(): String {
        val params = parameters.joinToString()
        return "$name($params): $returnType"
    }
}

data class MethodParameter(
    val name: String,
    val type: Type,
    val isOptional: Boolean = false,
) {
    override fun toString(): String {
        return "$name${if (isOptional) "?" else ""}: $type"
    }
}
