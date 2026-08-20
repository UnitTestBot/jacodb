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

import org.jacodb.ets.dto.ArrayTypeDto
import org.jacodb.ets.dto.TypeDto
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsType

/**
 * Creates the canonical DTO representation of an array type.
 *
 * [ArrayTypeDto] itself is a wire-format data class and therefore permits nested array
 * element types. Conversion and expression code use this helper to preserve the model
 * invariant: dimensions are accumulated in one outer array type.
 */
internal tailrec fun TypeDto.toArrayType(dimensions: Int): ArrayTypeDto = when (this) {
    is ArrayTypeDto -> elementType.toArrayType(dimensions + this.dimensions)
    else -> ArrayTypeDto(elementType = this, dimensions = dimensions)
}

/** Canonical model counterpart of [TypeDto.toArrayType]. */
internal tailrec fun EtsType.toArrayType(dimensions: Int): EtsArrayType = when (this) {
    is EtsArrayType -> elementType.toArrayType(dimensions + this.dimensions)
    else -> EtsArrayType(elementType = this, dimensions = dimensions)
}
