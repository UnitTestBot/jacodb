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

package org.jacodb.ets.model

import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.ets.base.EtsType

/**
 * Precompiled [Regex] for `.d.ts` and `.ts` file extensions.
 */
private val REGEX_TS_SUFFIX: Regex = """(\.d\.ts|\.ts)$""".toRegex()

data class EtsFileSignature(
    val projectName: String,
    val fileName: String,
) {
    override fun toString(): String {
        // Remove ".d.ts" and ".ts" file ext:
        val tmp = fileName.replace(REGEX_TS_SUFFIX, "")
        return if (projectName.isNotBlank()) {
            "@$projectName/$tmp"
        } else {
            tmp
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EtsFileSignature
        if (fileName != other.fileName) return false

        return true
    }

    override fun hashCode(): Int {
        return fileName.hashCode()
    }
}

data class EtsNamespaceSignature(
    val name: String,
    val file: EtsFileSignature? = null,
    val namespace: EtsNamespaceSignature? = null,
) {
    override fun toString(): String {
        return if (namespace != null) {
            "$namespace::$name"
        } else if (file != null) {
            "$file: $name"
        } else {
            name
        }
    }
}

data class EtsClassSignature(
    val name: String,
    val file: EtsFileSignature? = null,
    val namespace: EtsNamespaceSignature? = null,
) {
    // init {
    //     require(!(file != null && namespace != null)) {
    //         "Class cannot have both declaring file and declaring namespace"
    //     }
    // }

    override fun toString(): String {
        return if (namespace != null) {
            "$namespace::$name"
        } else if (file != null) {
            "$file: $name"
        } else {
            name
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EtsClassSignature

        if (name != other.name) return false
        if (file != other.file) return false
        if (namespace != other.namespace) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

data class EtsFieldSignature(
    val enclosingClass: EtsClassSignature,
    val sub: EtsFieldSubSignature,
) {
    val name: String
        get() = sub.name

    val type: EtsType
        get() = sub.type

    override fun toString(): String {
        return "${enclosingClass.name}::$sub"
    }
}

data class EtsFieldSubSignature(
    val name: String,
    val type: EtsType,
) {
    override fun toString(): String {
        return "$name: $type"
    }
}

data class EtsMethodSignature(
    val enclosingClass: EtsClassSignature,
    val name: String,
    val parameters: List<EtsMethodParameter>,
    val returnType: EtsType,
) {

    constructor(
        enclosingClass: EtsClassSignature,
        sub: EtsMethodSubSignature,
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

data class EtsMethodSubSignature(
    val name: String,
    val parameters: List<EtsMethodParameter>,
    val returnType: EtsType,
) {
    override fun toString(): String {
        val params = parameters.joinToString()
        return "$name($params): $returnType"
    }
}

data class EtsMethodParameter(
    val index: Int,
    val name: String,
    override val type: EtsType,
    val isOptional: Boolean = false,
) : CommonMethodParameter {
    override fun toString(): String {
        return "$name${if (isOptional) "?" else ""}: $type"
    }
}
