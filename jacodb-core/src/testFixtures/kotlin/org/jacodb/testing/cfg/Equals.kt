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

package org.jacodb.testing.cfg

class Equals {

    fun equals1(a: Float, b: Float) = a == b

    fun equals2(a: Float?, b: Float?) = a!! == b!!

    fun equals3(a: Float?, b: Float?) = a != null && b != null && a == b

    fun equals4(a: Float?, b: Float?) = if (a is Float && b is Float) a == b else null!!

    fun equals5(a: Any?, b: Any?) = if (a is Float && b is Float) a == b else null!!


    fun box(): String {
        if (-0.0F != 0.0F) return "fail 0"
        if (!equals1(-0.0F, 0.0F)) return "fail 1"
        if (!equals2(-0.0F, 0.0F)) return "fail 2"
        if (!equals3(-0.0F, 0.0F)) return "fail 3"
        if (!equals4(-0.0F, 0.0F)) return "fail 4"

        // Smart casts behavior in 1.2
        if (equals5(-0.0F, 0.0F)) return "fail 5"

        return "OK"
    }

}