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

package org.jacodb.impl.bytecode

import org.jacodb.api.JcAccessible
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.JcLookup
import org.jacodb.api.JcLookupExtFeature
import org.jacodb.api.JcMethod
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.TypeName
import org.jacodb.api.ext.hasAnnotation
import org.jacodb.api.ext.packageName

abstract class JcAbstractLookup<Entry : JcAccessible, Result : JcAccessible>(protected var entry: Entry) {

    private var allowSearchPrivate: Boolean = true
    private val enterPointPackageName: String = entry.resolvePackage
    private var currentPackageName = entry.resolvePackage

    abstract val predicate: (Result) -> Boolean
    abstract val Entry.resolvePackage: String
    abstract fun Entry.next(): List<Entry>
    abstract val Entry.elements: List<Result>

    protected open fun lookupElement(en: Entry): Result? = en.elements.firstOrNull { matches(it) }

    private fun transit(entry: Entry, searchPrivate: Boolean) {
        this.entry = entry
        this.currentPackageName = entry.resolvePackage
        this.allowSearchPrivate = searchPrivate
    }

    fun lookup(): Result? {
        var workingList = listOf(entry)
        var searchPrivate = true
        while (workingList.isNotEmpty()) {
            workingList.forEach {
                transit(it, searchPrivate)
                lookupElement(it)?.let {
                    return it
                }
            }
            searchPrivate = false
            workingList = workingList.flatMap { it.next() }
        }
        return null
    }

    private fun matches(result: Result): Boolean {
        if (allowSearchPrivate) {
            return predicate(result)
        }
        return (result.isPublic || result.isProtected ||
            (result.isPackagePrivate && currentPackageName == enterPointPackageName)) && predicate(result)

    }

}

internal interface PolymorphicSignatureSupport {
    fun List<JcMethod>.indexOf(name: String): Int {
        if (isEmpty()) {
            return -1
        }
        val packageName = first().enclosingClass.packageName
        if (packageName == "java.lang.invoke") {
            return indexOfFirst {
                it.name == name && it.hasAnnotation("java.lang.invoke.MethodHandle\$PolymorphicSignature")
            } // weak consumption. may fail
        }
        return -1
    }

    fun List<JcMethod>.find(name: String, description: String): JcMethod? {
        val index = indexOf(name)
        return if (index >= 0) get(index) else null
    }

    fun List<JcTypedMethod>.find(name: String): JcTypedMethod? {
        val index = map { it.method }.indexOf(name)
        return if (index >= 0) get(index) else null
    }
}

abstract class DelegatingLookup<Field : JcAccessible, Method : JcAccessible>(
    private val ext: List<JcLookupExtFeature>,
    private val delegate: JcLookup<Field, Method>,
) : JcLookup<Field, Method> {

    abstract fun lookupOf(feature: JcLookupExtFeature): JcLookup<Field, Method>

    override fun field(name: String): Field? {
        return delegateCall { field(name) }
    }

    override fun field(name: String, typeName: TypeName?): Field? {
        return delegateCall { field(name, typeName) }
    }

    override fun method(name: String, description: String): Method? {
        return delegateCall { method(name, description) }
    }

    override fun staticMethod(name: String, description: String): Method? {
        return delegateCall { staticMethod(name, description) }
    }

    override fun specialMethod(name: String, description: String): Method? {
        return delegateCall { specialMethod(name, description) }
    }

    private inline fun <Result> delegateCall(call: JcLookup<Field, Method>.() -> Result?): Result? {
        val result = delegate.call()
        if (result == null) {
            ext.forEach { e ->
                lookupOf(e).call()?.let {
                    return it
                }
            }
        }
        return result
    }

}

class ClassDelegatingLookup(
    private val clazz: JcClassOrInterface, ext: List<JcLookupExtFeature>,
    delegate: JcLookup<JcField, JcMethod>,
) : DelegatingLookup<JcField, JcMethod>(ext, delegate) {
    override fun lookupOf(feature: JcLookupExtFeature): JcLookup<JcField, JcMethod> = feature.lookup(clazz)
}

class TypeDelegatingLookup(
    private val type: JcClassType, ext: List<JcLookupExtFeature>,
    delegate: JcLookup<JcTypedField, JcTypedMethod>,
) : DelegatingLookup<JcTypedField, JcTypedMethod>(ext, delegate) {
    override fun lookupOf(feature: JcLookupExtFeature): JcLookup<JcTypedField, JcTypedMethod> = feature.lookup(type)
}
