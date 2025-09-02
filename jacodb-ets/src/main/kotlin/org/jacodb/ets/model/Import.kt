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

    init {
        if (type == EtsImportType.SIDE_EFFECT) {
            require(name.isEmpty()) { "Side-effect imports should have empty name" }
            require(nameBeforeAs == null) { "Side-effect imports should not have nameBeforeAs" }
        } else {
            require(name.isNotEmpty()) { "Only side-effect imports can have empty name" }
        }
    }

    // Note: Import statements do not have decorators in JS/TS.
    override val decorators: List<EtsDecorator> get() = emptyList()

    /**
     * Import clause name without any aliasing.
     */
    val originalName: String
        get() = nameBeforeAs ?: name

    /**
     * Whether this is a default import.
     *
     * ```ts
     * import React from 'react';
     * import { default as React } from 'react';
     * ```
     */
    val isDefaultImport: Boolean
        get() = type == EtsImportType.DEFAULT

    /**
     * Whether this is a named import.
     *
     * ```ts
     * import { useState } from 'react';
     * import { Component as ReactComponent } from 'react';
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
        get() = type == EtsImportType.NAMESPACE

    /**
     * Whether this is a side-effect import.
     *
     * ```ts
     * import './styles.css';
     * ```
     */
    val isSideEffectImport: Boolean
        get() = type == EtsImportType.SIDE_EFFECT

    override fun toString(): String = when(type) {
        EtsImportType.DEFAULT -> {
            // Default import: import React from 'react'
            "import $name from '$from'"
        }

        EtsImportType.NAMED -> {
            // Named import:
            //   import { useState } from 'react'
            //   import { Component as ReactComponent } from 'react'
            val alias = if (name != originalName) " as $name" else ""
            "import { $originalName$alias } from '$from'"
        }

        EtsImportType.NAMESPACE -> {
            // Namespace import: import * as Utils from './utils'
            "import * as $name from '$from'"
        }

        EtsImportType.SIDE_EFFECT -> {
            // Side effect import: import './styles.css'
            "import '$from'"
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
