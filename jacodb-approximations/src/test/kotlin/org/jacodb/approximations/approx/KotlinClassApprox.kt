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

package org.jacodb.approximations.approx

import org.jacodb.approximation.annotation.Approximate
import org.jacodb.approximations.target.ClassForField
import org.jacodb.approximations.target.KotlinClass
import org.jetbrains.annotations.NotNull

@Approximate(KotlinClass::class)
class KotlinClassApprox {
    @NotNull
    private val artificialField: ClassForField = ClassForField()
    private val fieldToReplace: Int = 3
    private val sameApproximation: KotlinClassApprox? = null
    private val anotherApproximation: IntegerApprox? = null

    fun replaceBehaviour(value: Int): Int = 42

    fun artificialMethod(): Int = 1 + 2 * 3

    fun useArtificialField(classForField: ClassForField): Int {
        if (classForField == artificialField) {
            return fieldToReplace
        }

        return 0
    }

    fun useSameApproximationTarget(kotlinClass: KotlinClass): Int {
        if (sameApproximation == null) return 0

        if (kotlinClass.methodWithoutApproximation() == sameApproximation.artificialMethod()) {
            return 42
        }

        return 1
    }

    fun useAnotherApproximationTarget(value: Int): Int {
        if (anotherApproximation == null) return 0

        if (anotherApproximation.value == value) {
            return 42
        }

        return 1
    }

    fun useFieldWithoutApproximation(classForField: ClassForField): Int {
        if (classForField == artificialField) {
            return 1
        }

        return 2
    }
}