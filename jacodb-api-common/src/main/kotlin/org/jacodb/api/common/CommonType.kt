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

package org.jacodb.api.common

interface CommonType {
    val typeName: String

    /**
     * Nullability of a type:
     * - `true` for `T?` (nullable Kotlin type),
     * - `false` for `T` (non-nullable Kotlin type)
     * - `null` for `T!` (platform type, means `T or T?`)
     */
    val nullable: Boolean?
}

interface CommonTypeName {
    val typeName: String
}

interface CommonRefType : CommonType

interface CommonArrayType : CommonRefType {
    val elementType: CommonType
    val dimensions: Int
}

interface CommonClassType : CommonRefType
