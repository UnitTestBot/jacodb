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
import org.jacodb.api.JcClassExtFeature
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.fs.ClassSourceImpl
import org.jacodb.impl.fs.LazyClassSourceImpl
import org.jacodb.impl.fs.fullAsmNode
import org.jacodb.impl.fs.info
import org.jacodb.impl.types.ClassInfo
import org.jacodb.impl.weakLazy
import org.objectweb.asm.tree.ClassNode
import java.util.*
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JcClassOrInterfaceImpl(
    override val classpath: JcClasspath,
    private val classSource: ClassSource,
    private val featuresChain: JcFeaturesChain,
) : JcClassOrInterface {

    private val hasClassFeatures = featuresChain.features.any { it is JcClassExtFeature }

    private val cachedInfo: ClassInfo? = when (classSource) {
        is LazyClassSourceImpl -> classSource.info // that means that we are loading bytecode. It can be removed let's cache info
        is ClassSourceImpl -> classSource.info // we can easily read link let's do it
        else -> null // maybe we do not need to do right now
    }

    private val extensionData by lazy(PUBLICATION) {
        HashMap<String, Any>().also { map ->
            featuresChain.newRequest().run<JcClassExtFeature> {
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

    override val interfaces: List<JcClassOrInterface>
        get() {
            return info.interfaces.map {
                classpath.findClass(it)
            }
        }

    override val superClass: JcClassOrInterface?
        get() {
            return info.superClass?.let {
                classpath.findClass(it)
            }
        }

    override val outerClass: JcClassOrInterface?
        get() {
            return info.outerClass?.className?.let {
                classpath.findClass(it)
            }
        }

    override val innerClasses: List<JcClassOrInterface>
        get() {
            return info.innerClasses.map {
                classpath.findClass(it)
            }
        }

    override val access: Int
        get() = info.access

    private val lazyAsmNode: ClassNode by weakLazy {
        classSource.fullAsmNode
    }

    override fun asmNode() = lazyAsmNode
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

    override val declaredFields: List<JcField>
        get() {
            val default = info.fields.map { JcFieldImpl(this, it) }
            if (hasClassFeatures) {
                val result = TreeSet<JcField> { o1, o2 -> o1.name.compareTo(o2.name) }
                featuresChain.newRequest().run<JcClassExtFeature> {
                    it.fieldsOf(this, default)?.let {
                        result.addAll(it)
                    }
                }
                result.addAll(default)
                return result.toList()
            }
            return default
        }

    override val declaredMethods: List<JcMethod> by lazy(PUBLICATION) {
        val default = info.methods.map { toJcMethod(it, featuresChain) }
        if (hasClassFeatures) {
            val result = TreeSet<JcMethod> { o1, o2 -> (o1.name + o1.description).compareTo(o2.name + o2.description) }
            featuresChain.newRequest().run<JcClassExtFeature> {
                it.methodsOf(this, default)?.let {
                    result.addAll(it)
                }
            }
            result.addAll(default)
            result.toList()
        } else {
            default
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

    override fun toString(): String {
        return "(id:${declaration.location.id})$name"
    }
}