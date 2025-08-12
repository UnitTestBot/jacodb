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

/**
 * Represents import information for TypeScript/JavaScript imports.
 *
 * @property name The name that will be used in the importing module (empty for side-effect imports)
 * @property type The [type][EtsImportType] of import.
 * @property from The module or path being imported from.
 * @property nameBeforeAs The original name before 'as' aliasing (null if no aliasing, "*" for namespace imports).
 * @property modifiers Import modifiers.
 */
data class EtsImportInfo(
    val name: String,
    val type: EtsImportType,
    val from: String,
    val nameBeforeAs: String? = null,
    override val modifiers: EtsModifiers = EtsModifiers.EMPTY,
) : Base {

    // Note: Import statements do not have decorators in JS/TS.
    override val decorators: List<EtsDecorator> = emptyList()

    /**
     * Import clause name without any aliasing.
     */
    val originalName: String = nameBeforeAs ?: name

    /**
     * Whether this is a default import.
     *
     * ```ts
     * import React from 'react';
     * ```
     */
    val isDefaultImport: Boolean
        get() = type == EtsImportType.DEFAULT || originalName == "default"

    /**
     * Whether this is a named import.
     *
     * ```ts
     * import { useState } from 'react';
     * ```
     */
    val isNamedImport: Boolean
        get() = type == EtsImportType.NAMED

    /**
     * Whether this is a namespace import.
     *
     * ```ts
     * import * as Utils from './utils';
     * ```
     */
    val isNamespaceImport: Boolean
        get() = type == EtsImportType.NAMESPACE || nameBeforeAs == "*"

    /**
     * Whether this is a side-effect import.
     *
     * ```ts
     * import './styles.css';
     * ```
     */
    val isSideEffectImport: Boolean
        get() = type == EtsImportType.SIDE_EFFECT

    /**
     * Whether this import uses aliasing.
     *
     * ```ts
     * import { Component as ReactComponent };
     * ```
     */
    val isAliased: Boolean
        get() = nameBeforeAs != null && nameBeforeAs != "*" && nameBeforeAs != name

    override val isDefault: Boolean
        get() = isDefaultImport || super.isDefault

    override fun toString(): String = buildString {
        append("import ")

        when {
            isSideEffectImport -> {
                // Side effect import: import './styles.css'
                append("'$from'")
            }

            isNamespaceImport -> {
                // Namespace import: import * as Utils from './utils'
                append("* as $name from '$from'")
            }

            isAliased -> {
                // Aliased import: import { Component as ReactComponent } from 'react'
                append("{ $originalName as $name } from '$from'")
            }

            isNamedImport -> {
                // Named import: import { useState } from 'react'
                append("{ $name } from '$from'")
            }

            isDefaultImport -> {
                // Default import: import React from 'react'
                append("$name from '$from'")
            }
        }
    }
}

/**
 * Type of import in TypeScript/JavaScript.
 */
enum class EtsImportType {
    /**
     * Default import:
     * ```ts
     * import React from 'react'
     * ```
     */
    DEFAULT,

    /**
     * Named import:
     * ```ts
     * import { useState } from 'react'
     * ```
     */
    NAMED,

    /**
     * Namespace import:
     * ```ts
     * import * as Utils from './utils'
     * ```
     */
    NAMESPACE,

    /**
     * Side-effect import:
     * ```ts
     * import './styles.css'
     * ```
     */
    SIDE_EFFECT,
}
