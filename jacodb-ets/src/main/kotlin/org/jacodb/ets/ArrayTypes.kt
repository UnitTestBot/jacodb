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

package org.jacodb.ets

internal data class FlattenedArrayType<T>(
    val elementType: T,
    val dimensions: Int,
)

internal tailrec fun <T> flattenArrayType(
    elementType: T,
    dimensions: Int,
    nestedArray: (T) -> FlattenedArrayType<T>?,
): FlattenedArrayType<T> {
    val nested = nestedArray(elementType) ?: return FlattenedArrayType(elementType, dimensions)
    return flattenArrayType(nested.elementType, dimensions + nested.dimensions, nestedArray)
}
