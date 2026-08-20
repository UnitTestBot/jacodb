/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * Name conventions shared with the Kotlin side.
 * Mirrors `org.jacodb.ets.utils.Constants` (jacodb-ets/src/main/kotlin/org/jacodb/ets/utils/Constants.kt).
 */

export const CONSTRUCTOR_NAME = "constructor";
export const DEFAULT_ARK_CLASS_NAME = "%dflt";
export const DEFAULT_ARK_METHOD_NAME = "%dflt";
export const UNKNOWN_PROJECT_NAME = "%unk";
export const UNKNOWN_FILE_NAME = "%unk";
export const UNKNOWN_NAMESPACE_NAME = "%unk";
export const UNKNOWN_CLASS_NAME = "";
export const UNKNOWN_FIELD_NAME = "";
export const UNKNOWN_METHOD_NAME = "";
export const INSTANCE_INIT_METHOD_NAME = "%instInit";
export const STATIC_INIT_METHOD_NAME = "%statInit";
export const ANONYMOUS_CLASS_PREFIX = "%AC";
export const ANONYMOUS_METHOD_PREFIX = "%AM";
export const TEMP_LOCAL_PREFIX = "%";
/** Synthetic name of a destructuring (pattern) parameter; the index keeps names distinct. */
export const PATTERN_PARAMETER_PREFIX = "%pat";
/** Synthetic name of a computed (non-literal) member. */
export const COMPUTED_MEMBER_NAME = "%computed";

/**
 * Local names with this prefix are reserved by the Kotlin `Convert.kt`
 * (its own three-address lowering introduces `_tmpN` locals),
 * so the frontend must never emit them.
 */
export const FORBIDDEN_LOCAL_PREFIX = "_tmp";

/**
 * Modifier bitmask. Mirrors `org.jacodb.ets.model.EtsModifiers`
 * (jacodb-ets/src/main/kotlin/org/jacodb/ets/model/Modifiers.kt).
 */
export const Modifier = {
    PRIVATE: 1 << 0,
    PROTECTED: 1 << 1,
    PUBLIC: 1 << 2,
    EXPORT: 1 << 3,
    STATIC: 1 << 4,
    ABSTRACT: 1 << 5,
    ASYNC: 1 << 6,
    CONST: 1 << 7,
    ACCESSOR: 1 << 8,
    DEFAULT: 1 << 9,
    IN: 1 << 10,
    READONLY: 1 << 11,
    OUT: 1 << 12,
    OVERRIDE: 1 << 13,
    DECLARE: 1 << 14,
} as const;

export type ModifierName = keyof typeof Modifier;

/**
 * Class category. Mirrors `org.jacodb.ets.model.EtsClassCategory`
 * (values are the serialized ints expected by `ClassDto.category`).
 */
export const ClassCategory = {
    CLASS: 0,
    STRUCT: 1,
    INTERFACE: 2,
    ENUM: 3,
    TYPE_LITERAL: 4,
    OBJECT: 5,
} as const;

export type ClassCategoryValue = (typeof ClassCategory)[keyof typeof ClassCategory];

/**
 * Export type ints expected by `ExportInfoDto.exportType` (see Kotlin `Convert.kt`).
 */
export const ExportType = {
    NAMESPACE: 0,
    CLASS: 1,
    METHOD: 2,
    LOCAL: 3,
    TYPE: 4,
    UNKNOWN: 9,
} as const;

export type ExportTypeValue = (typeof ExportType)[keyof typeof ExportType];

/**
 * Import type strings expected by `ImportInfoDto.importType` (see Kotlin `Convert.kt`):
 * "Identifier" (default import), "NamedImports", "NamespaceImport", "" (side-effect import).
 */
export type ImportType = "Identifier" | "NamedImports" | "NamespaceImport" | "";
