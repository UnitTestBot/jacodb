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

package org.jacodb.approximations.target

class KotlinClass {
    private val fieldToReplace: Int = 42
    private val sameApproximationTarget: KotlinClass? = null
    private val anotherApproximationTarget: Int? = null
    private val fieldWithoutApproximation: ClassForField = ClassForField()

    fun replaceBehaviour(value: Int): Int {
        if (value == fieldToReplace) {
            return -1
        }

        return -2
    }

    fun methodWithoutApproximation(): Int = 42

    fun useSameApproximationTarget(kotlinClass: KotlinClass): Int {
        if (kotlinClass == sameApproximationTarget) {
            return 1
        }

        return 0
    }

    fun useAnotherApproximationTarget(value: Int): Int {
        if (value == anotherApproximationTarget) {
            return 1
        }

        return 0
    }

    fun useFieldWithoutApproximation(classForField: ClassForField): Int {
        if (classForField == fieldWithoutApproximation) {
            return 1
        }

        return 0
    }
}

class ClassForField
