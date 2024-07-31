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

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findMethodOrNull
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

    private val cachedInfo: ClassInfo? = when (classSource) {
        is LazyClassSourceImpl -> classSource.info // that means that we are loading bytecode. It can be removed let's cache info
        is ClassSourceImpl -> classSource.info // we can easily read link let's do it
        else -> null // maybe we do not need to do right now
    }

    private val interfacesArray: Array<JcClassOrInterface> by lazy(PUBLICATION) {
        info.interfaces.map { classpath.findClass(it) }.toTypedArray()
    }

    private val innerClassesArray: Array<JcClassOrInterface> by lazy(PUBLICATION) {
        info.innerClasses.filter { it != name }.map { classpath.findClass(it) }.toTypedArray()
    }

    override val lookup: JcLookup<JcField, JcMethod> = ClassDelegatingLookup(
        this,
        featuresChain.classLookups,
        JcClassLookupImpl(this)
    )

    private val extensionData by lazy(PUBLICATION) {
        HashMap<String, Any>().also { map ->
            featuresChain.run<JcClassExtFeature> {
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

    override val interfaces: List<JcClassOrInterface> get() = interfacesArray.asList()

    override val superClass: JcClassOrInterface? by lazy(PUBLICATION) {
        info.superClass?.let { classpath.findClass(it) }
    }

    override val outerClass: JcClassOrInterface? by lazy(PUBLICATION) {
        info.outerClass?.className?.let { classpath.findClass(it) }
    }

    override val innerClasses: List<JcClassOrInterface> get() = innerClassesArray.asList()

    override val access: Int
        get() = info.access

    private val lazyAsmNode: ClassNode by weakLazy {
        classSource.fullAsmNode
    }

    override fun <T> withAsmNode(body: (ClassNode) -> T): T {
        val asmNode = lazyAsmNode
        return synchronized(asmNode) {
            body(asmNode)
        }
    }

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

    override val declaredFields: List<JcField> by lazy(PUBLICATION) {
        val default = info.fields.map { JcFieldImpl(this, it) }
        default.joinFeatureFields(this, featuresChain)
    }


    override val declaredMethods: List<JcMethod> by lazy(PUBLICATION) {
        val default = info.methods.map { toJcMethod(it, featuresChain) }
        default.joinFeatureMethods(this, featuresChain)
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

fun List<JcField>.joinFeatureFields(
    jcClassOrInterface: JcClassOrInterface,
    featuresChain: JcFeaturesChain
): List<JcField> {
    val hasClassFeatures = featuresChain.features.any { it is JcClassExtFeature }
    if (hasClassFeatures) {
        val additional = TreeSet<JcField> { o1, o2 -> o1.name.compareTo(o2.name) }
        featuresChain.run<JcClassExtFeature> {
            it.fieldsOf(jcClassOrInterface, this)?.let {
                additional.addAll(it)
            }
        }
        if (additional.isNotEmpty()) {
            return appendOrOverride(additional) { it.name }
        }
    }
    return this
}

fun List<JcMethod>.joinFeatureMethods(
    jcClassOrInterface: JcClassOrInterface,
    featuresChain: JcFeaturesChain
): List<JcMethod> {
    val hasClassFeatures = featuresChain.features.any { it is JcClassExtFeature }
    if (hasClassFeatures) {
        val additional = TreeSet<JcMethod> { o1, o2 ->
            o1.uniqueName.compareTo(o2.uniqueName)
        }
        featuresChain.run<JcClassExtFeature> {
            it.methodsOf(jcClassOrInterface, this)?.let {
                additional.addAll(it)
            }
        }
        if (additional.isNotEmpty()) {
            return appendOrOverride(additional) { it.uniqueName }
        }
    }
    return this
}

private val JcMethod.uniqueName: String get() = name + description

private inline fun <T> List<T>.appendOrOverride(additional: Set<T>, getKey: (T) -> String): List<T> {
    if (additional.isNotEmpty()) {
        val additionalMap = additional.associateBy(getKey).toMutableMap()
        // we need to preserve order
        return map {
            val uniqueName = getKey(it)
            additionalMap[uniqueName]?.also {
                additionalMap.remove(uniqueName)
            } ?: it
        } + additionalMap.values
    }
    return this
}
