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

package org.jacodb.approximations

import org.jacodb.api.jvm.JavaVersion
import org.jacodb.api.jvm.cfg.*
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findDeclaredFieldOrNull
import org.jacodb.approximation.*
import org.jacodb.approximation.Approximations.findApproximationByOriginOrNull
import org.jacodb.approximation.Approximations.findOriginalByApproximationOrNull
import org.jacodb.approximations.target.KotlinClass
import org.jacodb.impl.fs.JarLocation
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawFieldRef
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.guavaLib
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class ApproximationsTest : BaseTest() {

    companion object : WithDB(Approximations)

    @Test
    fun `kotlin approximation`() {
        val classes = cp.findClass<KotlinClass>()

        val originalClassName = KotlinClass::class.qualifiedName!!.toOriginalName()
        val approximation = findApproximationByOriginOrNull(originalClassName)

        assertNotNull(approximation)
        assertEquals(classes.name, findOriginalByApproximationOrNull(approximation!!.toApproximationName()))
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Test
    fun `java approximation`() {
        val classec = cp.findClass<Integer>()

        val originalClassName = "java.lang.Integer".toOriginalName()
        val approximation = findApproximationByOriginOrNull(originalClassName)

        assertNotNull(approximation)
        assertEquals(classec.name, findOriginalByApproximationOrNull(approximation!!.toApproximationName()))
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Test
    fun `integer approximation`() {
        val classec = cp.findClass<Integer>()

        val field = classec.findDeclaredFieldOrNull("value")
        assertTrue(field is JcEnrichedVirtualField)

        val method = classec.declaredMethods
            .filter { it.name == "valueOf" }
            .singleOrNull { it is JcEnrichedVirtualMethod }
        assertNotNull(method)
    }

    @Test
    fun `replaced fields`() {
        val classec = cp.findClass<KotlinClass>()
        val fields = classec.declaredFields

        assertTrue(fields.size == 7)

        val (virtualFields, originalFields) = fields.partition { it is JcEnrichedVirtualField }
        val virtualFieldsNames = virtualFields.map { it.name }
        val originalFieldsNames = originalFields.map { it.name }

        assertTrue(virtualFields.size == 4)
        assertTrue("fieldToReplace" in virtualFieldsNames)
        assertTrue("sameApproximation" in virtualFieldsNames)
        assertTrue("anotherApproximation" in virtualFieldsNames)
        assertTrue("artificialField" in virtualFieldsNames)

        assertTrue(originalFields.size == 3)
        assertTrue("sameApproximationTarget" in originalFieldsNames)
        assertTrue("anotherApproximationTarget" in originalFieldsNames)
        assertTrue("fieldWithoutApproximation" in originalFieldsNames)

        assertEquals(fields, classec.declaredFields)
    }

    @Test
    fun `replaced methods`() {
        val classec = cp.findClass<KotlinClass>()
        val methods = classec.declaredMethods

        assertTrue(methods.size == 8)

        val (virtualMethods, originalMethods) = methods.partition { it is JcEnrichedVirtualMethod }
        // we can use just names here since we don't have overload in both original and target classes
        val virtualMethodsNames = virtualMethods.map { it.name }
        val originalMethodsNames = originalMethods.map { it.name }

        assertTrue(virtualMethods.size == 7)
        assertTrue("replaceBehaviour" in virtualMethodsNames)
        assertTrue("artificialMethod" in virtualMethodsNames)
        assertTrue("useArtificialField" in virtualMethodsNames)
        assertTrue("useSameApproximationTarget" in virtualMethodsNames)
        assertTrue("useAnotherApproximationTarget" in virtualMethodsNames)
        assertTrue("useFieldWithoutApproximation" in virtualMethodsNames)
        assertTrue("<init>" in virtualMethodsNames)

        assertTrue(originalMethods.size == 1)
        assertTrue("methodWithoutApproximation" in originalMethodsNames)

        assertEquals(methods, classec.declaredMethods)
    }

    @Test
    fun `replace approximations in methodBody`() {
        val classec = cp.findClass<KotlinClass>()
        val method = classec.declaredMethods.single { it.name == "useSameApproximationTarget" }

        val graph = method.flowGraph()
        val instructions = graph.instructions
        val rawInstructions = method.rawInstList

        assertTrue(method.enclosingClass === classec)
        assertTrue("KotlinClassApprox" !in method.description)

        val types = hashSetOf<String>()

        types += method.returnType.typeName

        val callInsts = instructions.filterIsInstance<JcCallInst>()
        val assignInsts = instructions.filterIsInstance<JcAssignInst>()

        val rawCallInsts = rawInstructions.filterIsInstance<JcRawCallInst>()
        val rawAssignInsts = rawInstructions.filterIsInstance<JcRawAssignInst>()

        callInsts.forEach { inst ->
            val location = inst.location
            assertTrue(location.method === method)

            val callExpr = inst.callExpr
            types += callExpr.type.typeName
            types += callExpr.method.returnType.typeName
            types += callExpr.method.enclosingType.typeName
            types += callExpr.method.typeArguments.map { it.typeName }
        }

        assignInsts.forEach { inst ->
            val location = inst.location
            assertTrue(location.method === method)

            types += inst.lhv.type.typeName
            val rhv = inst.rhv
            if (rhv is JcFieldRef) {
                types += rhv.type.typeName
                types += rhv.field.fieldType.typeName
                types += rhv.field.enclosingType.typeName
            }
        }

        rawCallInsts.forEach { inst ->
            val location = inst.owner
            assertTrue(location === method)

            val callExpr = inst.callExpr
            types += callExpr.typeName.typeName
            types += callExpr.returnType.typeName
            types += callExpr.args.map { it.typeName.typeName }
        }

        rawAssignInsts.forEach { inst ->
            val location = inst.owner
            assertTrue(location === method)

            types += inst.lhv.typeName.typeName
            val rhv = inst.rhv
            if (rhv is JcRawFieldRef) {
                types += rhv.typeName.typeName
                rhv.instance?.typeName?.let { types += it.typeName }
                types += rhv.declaringClass.typeName
            }
        }

        assertTrue(types.none { findOriginalByApproximationOrNull(it.toApproximationName()) != null })
    }

    @Test
    fun `run around guava`() {
        runAlongLib(guavaLib)
    }

    private fun runAlongLib(file: File) {
        val classes = JarLocation(file, isRuntime = false, object : JavaVersion {
            override val majorVersion: Int
                get() = 8
        }).classes
        assertNotNull(classes)
        classes!!.forEach {
            val clazz = cp.findClass(it.key)
            if (!clazz.isAnnotation && !clazz.isInterface) {
                println("Testing class: ${it.key}")
                clazz.declaredMethods.forEach {
                    it.flowGraph()
                }
            }
        }
    }
}