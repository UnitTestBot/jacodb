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

package org.jacodb.panda.dynamic.api

import org.jacodb.api.common.CommonClass
import org.jacodb.api.common.CommonField

class PandaClass(
    val signature: PandaClassSignature,
    val superClassName: String? = null,
    val fields: List<PandaField> = emptyList(),
    val methods: List<PandaMethod> = emptyList(),
) : CommonClass {

    constructor(
        name: String,
        methods: List<PandaMethod> = emptyList(),
    ) : this(
        signature = PandaClassSignature(name),
        methods = methods,
    )

    init {
        for (method in methods) {
            method.enclosingClass_ = this
        }
    }

    lateinit var project: PandaProject
        internal set

    override val name: String
        get() = signature.name

    val simpleName: String
        get() = signature.simpleName

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PandaClass

        if (signature != other.signature) return false

        return true
    }

    override fun hashCode(): Int {
        return signature.hashCode()
    }

    override fun toString(): String {
        return signature.toString()
    }
}

data class PandaClassSignature(
    val name: String,
) {
    val simpleName: String
        get() = name.substringAfterLast('.')

    override fun toString(): String {
        return simpleName
    }
}

data class PandaField(
    val signature: PandaFieldSignature,
) : CommonField {

    constructor(
        enclosingClassName: String,
        name: String,
        type: PandaType,
    ) : this(
        PandaFieldSignature(
            enclosingClass = PandaClassSignature(enclosingClassName),
            name = name,
            type = type
        )
    )

    override var enclosingClass: PandaClass? = null
        internal set

    override val name: String
        get() = signature.name

    override val type: PandaType
        get() = signature.type

    override fun toString(): String {
        return signature.toString()
    }
}

data class PandaFieldSignature(
    val enclosingClass: PandaClassSignature,
    val name: String,
    val type: PandaType,
) {
    override fun toString(): String {
        return "${enclosingClass.name}::$name: $type"
    }
}
