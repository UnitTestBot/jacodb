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

package org.jacodb.panda.dynamic.ark.model

interface ArkClass {
    val signature: ClassSignature
    val superClass: ArkClass?
    // val fields: List<FieldSignature>
    // val methods: List<MethodSignature>
    val fields: List<ArkField>
    val methods: List<ArkMethod>

    val name: String
        get() = signature.name
}

class ArkClassImpl(
    override val signature: ClassSignature,
    // override val fields: List<FieldSignature>,
    // override val methods: List<MethodSignature>,
    override val fields: List<ArkField>,
    override val methods: List<ArkMethod>,
): ArkClass {
    override var superClass: ArkClass? = null
}
