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

@file:JvmName("UnitResolversLibrary")
package org.jacodb.analysis.library

import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.packageName

val MethodUnitResolver = UnitResolver { method -> method }
val PackageUnitResolver = UnitResolver { method -> method.enclosingClass.packageName }
val SingletonUnitResolver = UnitResolver { _ -> Unit }

fun getClassUnitResolver(includeNested: Boolean): UnitResolver<JcClassOrInterface> {
    return ClassUnitResolver(includeNested)
}

private class ClassUnitResolver(private val includeNested: Boolean): UnitResolver<JcClassOrInterface> {
    override fun resolve(method: JcMethod): JcClassOrInterface {
        return if (includeNested) {
            generateSequence(method.enclosingClass) { it.outerClass }.last()
        } else {
            method.enclosingClass
        }
    }
}