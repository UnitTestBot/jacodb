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
 * Represents export information for TypeScript/JavaScript exports.
 *
 * @property name The name of the exported entity.
 * @property type The [type][EtsExportType] of export.
 * @property from The module or path being exported from (null for direct exports).
 * @property nameBeforeAs The original name before 'as' aliasing (null if no aliasing).
 * @property modifiers Export modifiers.
 */
data class EtsExportInfo(
    val name: String,
    val type: EtsExportType,
    val from: String? = null,
    val nameBeforeAs: String? = null,
    override val modifiers: EtsModifiers = EtsModifiers.EMPTY,
) : Base {

    // Note: Export statements do not have decorators in JS/TS.
    override val decorators: List<EtsDecorator> get() = emptyList()

    /**
     * Export clause name without any aliasing.
     */
    val originalName: String
        get() = nameBeforeAs ?: name

    /**
     * Whether this export is a default export.
     *
     * ```ts
     * export default value;
     * export { value as default };
     * export { default } from './module';
     * export { default as Name } from './module';
     * ```
     */
    val isDefaultExport: Boolean
        get() {
            // For re-exports:
            //   export { default } from './module'
            //   export { default as Name } from './module'
            if (from != null) return originalName == "default"

            // For direct exports:
            //   export default value
            //   export { value as default }
            return name == "default" || super.isDefault
        }

    /**
     * Whether this export is a re-export.
     */
    val isReExport: Boolean
        get() = from != null

    /**
     * Whether this export is a star re-export.
     *
     * ```ts
     * export * from './module';
     * export * as Utils from './utils';
     * ```
     */
    val isStarReExport: Boolean
        get() = isReExport && originalName == "*"

    /**
     * Whether this export is aliased.
     *
     * ```ts
     * export { value as Name } from './module';
     * export { default as Name } from './module';
     * export * as Utils from './utils';
     * ```
     */
    val isAliased: Boolean
        get() = name != originalName

    override val isDefault: Boolean
        get() = isDefaultExport

    override fun toString(): String {
        return when {
            // Re-exports
            from != null -> {
                val alias = if (isAliased) " as $name" else ""
                if (isStarReExport) {
                    "export *$alias from '$from'"
                } else {
                    "export { $originalName$alias } from '$from'"
                }
            }

            // Direct default export
            isDefaultExport -> {
                "export default $originalName"
            }

            // Direct named export
            else -> {
                val alias = if (isAliased) " as $name" else ""
                "export { $originalName$alias }"
            }
        }
    }
}

/**
 * Type of export in TypeScript/JavaScript.
 */
enum class EtsExportType {
    /**
     * Namespace export:
     * ```ts
     * export namespace MyNamespace { ... }
     * ```
     */
    NAMESPACE,

    /**
     * Class export:
     * ```ts
     * export class MyClass { ... }
     * ```
     */
    CLASS,

    /**
     * Function export:
     * ```ts
     * export function myFunction() { ... }
     * ```
     */
    METHOD,

    /**
     * Local variable/constant export:
     * ```ts
     * export const myVariable = 42;
     * export let myLet = 'hello';
     * export var myVar = true;
     * ```
     */
    LOCAL,

    /**
     * Type export:
     * ```ts
     * export type MyType = string | number;
     * ```
     */
    TYPE,

    /**
     * Unknown export type, fallback for unrecognized export patterns.
     */
    UNKNOWN;
}
