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

import org.jacodb.api.ClassSource
import org.jacodb.api.JcAnnotation
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathFeature
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.impl.fs.ClassSourceImpl
import org.jacodb.impl.fs.LazyClassSourceImpl
import org.jacodb.impl.fs.fullAsmNodeWithFrames
import org.jacodb.impl.fs.info
import org.jacodb.impl.types.ClassInfo

class JcClassOrInterfaceImpl(
    override val classpath: JcClasspath,
    private val classSource: ClassSource,
    private val features: List<JcClasspathFeature>?
) : JcClassOrInterface {

    private val cachedInfo: ClassInfo? = when (classSource) {
        is LazyClassSourceImpl -> classSource.info // that means that we are loading bytecode. It can be removed let's cache info
        is ClassSourceImpl -> classSource.info // we can easily read link let's do it
        else -> null // maybe we do not need to do right now
    }

    private val extensionData by lazy(LazyThreadSafetyMode.NONE) {
        HashMap<String, Any>().also { map ->
            features?.forEach {
                map.putAll(it.extensionValuesOf(this).orEmpty())
            }
        }
    }

    val info by lazy { cachedInfo ?: classSource.info }

    override val declaration = JcDeclarationImpl.of(location = classSource.location, this)

    override val name: String get() = classSource.className
    override val simpleName: String get() = classSource.className.substringAfterLast(".")

    override val signature: String?
        get() = info.signature

    override val annotations: List<JcAnnotation>
        get() = info.annotations.map { JcAnnotationImpl(it, classpath) }

    override val interfaces by lazy(LazyThreadSafetyMode.NONE) {
        info.interfaces.map {
            classpath.findClass(it)
        }
    }

    override val superClass by lazy(LazyThreadSafetyMode.NONE) {
        info.superClass?.let {
            classpath.findClass(it)
        }
    }

    override val outerClass by lazy(LazyThreadSafetyMode.NONE) {
        info.outerClass?.className?.let {
            classpath.findClass(it)
        }
    }

    override val innerClasses by lazy(LazyThreadSafetyMode.NONE) {
        info.innerClasses.map {
            classpath.findClass(it)
        }
    }

    override val access: Int
        get() = info.access

    override fun asmNode() = classSource.fullAsmNodeWithFrames(classpath)
    override fun bytecode(): ByteArray = classSource.byteCode

    override fun <T> extensionValue(key: String): T? {
        return extensionData[key] as? T
    }

    override val isAnonymous: Boolean
        get() {
            val outerClass = info.outerClass
            return outerClass != null && outerClass.name == null
        }

    override val outerMethod: JcMethod?
        get() {
            val info = info
            if (info.outerMethod != null && info.outerMethodDesc != null) {
                return outerClass?.findMethodOrNull(info.outerMethod, info.outerMethodDesc)
            }
            return null
        }

    override val declaredFields: List<JcField> by lazy(LazyThreadSafetyMode.NONE) {
        val result: List<JcField> = info.fields.map { JcFieldImpl(this, it) }
        when {
            !features.isNullOrEmpty() -> {
                val modifiedFields = result.toMutableList()
                features.forEach {
                    it.fieldsOf(this)?.let {
                        modifiedFields.addAll(it)
                    }
                }
                modifiedFields
            }
            else -> result
        }
    }

    override val declaredMethods: List<JcMethod> by lazy(LazyThreadSafetyMode.NONE) {
        val result: List<JcMethod> = info.methods.map { toJcMethod(it, classSource, features) }
        when {
            !features.isNullOrEmpty() -> {
                val modifiedMethods = result.toMutableList()
                features.forEach {
                    it.methodsOf(this)?.let {
                        modifiedMethods.addAll(it)
                    }
                }
                modifiedMethods
            }
            else -> result
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcClassOrInterfaceImpl) {
            return false
        }
        return other.name == name && other.declaration == declaration
    }

    override fun hashCode(): Int {
        return 31 * declaration.hashCode() + name.hashCode()
    }
}