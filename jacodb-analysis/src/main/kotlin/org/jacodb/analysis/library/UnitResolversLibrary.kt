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
import org.jacodb.api.core.CoreMethod
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.packageName

// TODO caelmbleidd add cache?????
fun <Method> methodUnitResolver() = UnitResolver<Method, Method> { method -> method }

// TODO caelmbleidd extract java
val JcPackageUnitResolver = UnitResolver<String, JcMethod> { method -> method.enclosingClass.packageName }
val JcSingletonUnitResolver = UnitResolver<Unit, JcMethod> { _ -> Unit }

fun getJcClassUnitResolver(includeNested: Boolean): UnitResolver<JcClassOrInterface, JcMethod> {
    return JcClassUnitResolver(includeNested)
}

private class JcClassUnitResolver(private val includeNested: Boolean): UnitResolver<JcClassOrInterface, JcMethod> {
    override fun resolve(method: JcMethod): JcClassOrInterface {
        return if (includeNested) {
            generateSequence(method.enclosingClass) { it.outerClass }.last()
        } else {
            method.enclosingClass
        }
    }
}