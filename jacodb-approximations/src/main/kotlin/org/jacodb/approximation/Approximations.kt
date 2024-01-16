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

package org.jacodb.approximation

import org.jacodb.api.jvm.ByteCodeIndexer
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcProject
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcFeature
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcInstExtFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcSignal
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.core.cfg.InstList
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.approximation.TransformerIntoVirtual.transformMethodIntoVirtual
import org.jacodb.approximation.annotation.Approximate
import org.jacodb.impl.cfg.InstListImpl
import org.jacodb.impl.fs.className
import org.jacodb.impl.storage.jooq.tables.references.ANNOTATIONS
import org.jacodb.impl.storage.jooq.tables.references.ANNOTATIONVALUES
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jooq.DSLContext
import org.objectweb.asm.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

// TODO we need somehow to remove approximation classes from the hierarchy to avoid presence of them in the inheritors of Object

/**
 *  A feature building mapping between so-called approximations and their targets.
 *  Other features like [ClassContentApproximationFeature] and [ApproximationsInstructionsFeature]
 *  uses it for modifying source code of target classes with information from
 *  the corresponding approximations.
 *
 *  Note: for correct work of the feature, you must wait
 *  till the whole processing of classpath is finished,
 *  otherwise you might get incomplete mapping.
 *  See [JcDatabase.awaitBackgroundJobs].
 */
object Approximations : JcFeature<Any?, Any?>, JcClassExtFeature, JcInstExtFeature {

    private val originalToApproximation: ConcurrentMap<OriginalClassName, ApproximationClassName> = ConcurrentHashMap()
    private val approximationToOriginal: ConcurrentMap<ApproximationClassName, OriginalClassName> = ConcurrentHashMap()

    override suspend fun query(classpath: JcProject, req: Any?): Sequence<Any?> {
        // returns an empty sequence for now, all requests are made using
        // findApproximationOrNull and findOriginalByApproximation functions
        return emptySequence()
    }

    override fun newIndexer(
        jcdb: JcDatabase,
        location: RegisteredLocation
    ): ByteCodeIndexer = ApproximationIndexer(originalToApproximation, approximationToOriginal)

    override fun onSignal(signal: JcSignal) {
        if (signal is JcSignal.BeforeIndexing) {
            val persistence = signal.jcdb.persistence
            persistence.read { jooq ->
                val approxSymbol = persistence.findSymbolId(approximationAnnotationClassName)
                jooq.select(CLASSES.NAME, ANNOTATIONVALUES.CLASS_SYMBOL)
                    .from(ANNOTATIONS)
                    .join(CLASSES).on(ANNOTATIONS.CLASS_ID.eq(CLASSES.ID))
                    .join(ANNOTATIONVALUES).on(ANNOTATIONVALUES.ANNOTATION_ID.eq(ANNOTATIONS.ID))
                    .where(
                        ANNOTATIONS.ANNOTATION_NAME.eq(approxSymbol).and(
                            ANNOTATIONVALUES.NAME.eq("value")
                        )
                    )
                    .fetch().forEach { (approximation, original) ->
                        val approximationClassName = persistence.findSymbolName(approximation!!).toApproximationName()
                        val originalClassName = persistence.findSymbolName(original!!).toOriginalName()
                        originalToApproximation[originalClassName] = approximationClassName
                        approximationToOriginal[approximationClassName] = originalClassName
                    }
            }
        }
    }


    /**
     * Returns a list of [JcEnrichedVirtualField] if there is an approximation for [clazz] and null otherwise.
     */
    override fun fieldsOf(clazz: JcClassOrInterface): List<JcField>? {
        val approximationName = findApproximationByOriginOrNull(clazz.name.toOriginalName()) ?: return null
        val approximationClass = clazz.classpath.findClassOrNull(approximationName) ?: return null

        return approximationClass.declaredFields.map { TransformerIntoVirtual.transformIntoVirtualField(clazz, it) }
    }

    /**
     * Returns a list of [JcEnrichedVirtualMethod] if there is an approximation for [clazz] and null otherwise.
     */
    override fun methodsOf(clazz: JcClassOrInterface): List<JcMethod>? {
        val approximationName = findApproximationByOriginOrNull(clazz.name.toOriginalName()) ?: return null
        val approximationClass = clazz.classpath.findClassOrNull(approximationName) ?: return null

        return approximationClass.declaredMethods.map {
            approximationClass.classpath.transformMethodIntoVirtual(clazz, it)
        }
    }

    override fun transformRawInstList(method: JcMethod, list: InstList<JcRawInst>): InstList<JcRawInst> {
        return InstListImpl(list.map { it.accept(InstSubstitutorForApproximations) })
    }

    /**
     * Returns a name of the approximation class for [className] if it exists and null otherwise.
     */
    fun findApproximationByOriginOrNull(
        className: OriginalClassName
    ): String? = originalToApproximation[className]?.className

    /**
     * Returns a name of the target class for [className] approximation if it exists and null otherwise.
     */
    fun findOriginalByApproximationOrNull(
        className: ApproximationClassName
    ): String? = approximationToOriginal[className]?.className
}

private class ApproximationIndexer(
    private val originalToApproximation: ConcurrentMap<OriginalClassName, ApproximationClassName>,
    private val approximationToOriginal: ConcurrentMap<ApproximationClassName, OriginalClassName>
) : ByteCodeIndexer {
    override fun index(classNode: ClassNode) {
        val annotations = classNode.visibleAnnotations ?: return

        // Check whether the classNode contains an approximation related annotation
        val approximationAnnotation = annotations.singleOrNull {
            approximationAnnotationClassName in it.desc.className
        } ?: return

        // Extract a name of the target class for this approximation
        val target = approximationAnnotation.values.filterIsInstance<org.objectweb.asm.Type>().single()

        val originalClassName = target.className.toOriginalName()
        val approximationClassName = classNode.name.className.toApproximationName()

        // Ensure that each approximation has one and only one
        require(originalClassName !in originalToApproximation) {
            "An error occurred during approximations indexing: you tried to add `$approximationClassName` " +
                    "as an approximation for `$originalClassName`, but the target class is already " +
                    "associated with approximation `${originalToApproximation[originalClassName]}`. " +
                    "Only bijection between classes is allowed."
        }
        require(approximationClassName !in approximationToOriginal) {
            "An error occurred during approximations indexing: you tried to add `$approximationClassName` " +
                    "as an approximation for `$originalClassName`, but this approximation is already used for " +
                    "`${approximationToOriginal[approximationClassName]}`. " +
                    "Only bijection between classes is allowed."
        }

        originalToApproximation[originalClassName] = approximationClassName
        approximationToOriginal[approximationClassName] = originalClassName
    }

    override fun flush(jooq: DSLContext) {
        // do nothing
    }
}

private val approximationAnnotationClassName = Approximate::class.qualifiedName!!

@JvmInline
value class ApproximationClassName(val className: String) {
    override fun toString(): String = className
}

@JvmInline
value class OriginalClassName(val className: String) {
    override fun toString(): String = className
}
