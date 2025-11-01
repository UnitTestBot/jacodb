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

package org.jacodb.ets.test.lang

import mu.KotlinLogging
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.test.TestBase
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Tests for class-related constructions.
 */
class EtsClassTest : TestBase() {

    @Test
    fun testSimpleClass() {
        val file = loadSourceFile("/samples/source/classes/SimpleClass.ts")

        // Find the SimpleClass
        val simpleClass = file.allClasses.find { it.name == "SimpleClass" }
        assertNotNull(simpleClass, "Should find SimpleClass")

        // Verify it has fields
        assertTrue(simpleClass.fields.isNotEmpty(), "SimpleClass should have fields")

        // Verify it has methods
        assertTrue(simpleClass.methods.isNotEmpty(), "SimpleClass should have methods")

        // Log first method for inspection
        if (simpleClass.methods.isNotEmpty()) {
            logMethodDetails(simpleClass.methods.first())
        }
    }

    @Test
    fun testConstructorClass() {
        val file = loadSourceFile("/samples/source/classes/Constructor.ts")

        val personClass = file.allClasses.find { it.name == "Person" }
        assertNotNull(personClass, "Should find class with constructor")

        // Find constructor method
        val constructor = personClass.ctor
        assertNotNull(constructor, "Should find constructor method")

        logMethodDetails(constructor)
        assertTrue(constructor.parameters.isNotEmpty(), "Constructor should have parameters")
    }

    @Test
    fun testFieldInitializers() {
        val file = loadSourceFile("/samples/source/classes/FieldInitializers.ts")

        // File contains Foo class with initialized fields
        val fooClass = file.allClasses.find { it.name == "Foo" }
        assertNotNull(fooClass, "Should find Foo class")

        // Verify fields with initializers exist
        assertTrue(fooClass.fields.size >= 2, "Foo should have at least 2 fields (x and y)")

        // Check for instance field x with initializer
        val fieldX = fooClass.fields.find { it.name == "x" }
        assertNotNull(fieldX, "Should find field x")

        // Check for static field y with initializer
        val fieldY = fooClass.fields.find { it.name == "y" && it.modifiers.isStatic }
        assertNotNull(fieldY, "Should find static field y")

        // Verify class has constructor and methods
        val constructor = fooClass.ctor
        assertNotNull(constructor, "Should find constructor")

        val fooMethod = fooClass.methods.find { it.name == "foo" }
        assertNotNull(fooMethod, "Should find foo method")

        val barMethod = fooClass.methods.find { it.name == "bar" && it.modifiers.isStatic }
        assertNotNull(barMethod, "Should find static bar method")
    }

    @Test
    fun testStaticClass() {
        val file = loadSourceFile("/samples/source/classes/StaticClass.ts")

        val staticClass = file.allClasses.find { it.name.contains("Static") }
        assertNotNull(staticClass, "Should find class with static members")

        // Check for static methods
        val staticMethods = staticClass.methods.filter { it.modifiers.isStatic }
        assertTrue(staticMethods.isNotEmpty(), "Class should have static methods")

        // Check for static fields
        val staticFields = staticClass.fields.filter { it.modifiers.isStatic }
        assertTrue(staticFields.isNotEmpty(), "Class should have static fields")
    }

    @Test
    fun testAbstractClass() {
        val file = loadSourceFile("/samples/source/classes/AbstractClass.ts")

        val abstractClass = file.allClasses.find {
            it.modifiers.isAbstract || it.name.contains("Abstract")
        }
        assertNotNull(abstractClass, "Should find abstract class")

        // Abstract classes may have abstract methods
        assertTrue(abstractClass.methods.isNotEmpty(), "Abstract class should have methods")
    }

    @Test
    fun testInheritanceClass() {
        val file = loadSourceFile("/samples/source/classes/InheritanceClass.ts")

        // Should have multiple classes (parent and child)
        assertTrue(file.allClasses.size >= 2, "Should have parent and child classes")

        // Find child class (should have superclass)
        val childClass = file.allClasses.find { it.superClass != null }
        assertNotNull(childClass, "Should find child class with inheritance")

        assertNotNull(childClass.superClass, "Child class should have superclass")
    }

    @Test
    fun testInterfaceImplementation() {
        val file = loadSourceFile("/samples/source/classes/InterfaceImplementation.ts")

        // Find interface
        val interfaceClass = file.allClasses.find {
            it.category == EtsClassCategory.INTERFACE
        }
        assertNotNull(interfaceClass, "Should find interface")

        // Find implementing class
        val implementingClass = file.allClasses.find {
            it.implementedInterfaces.isNotEmpty()
        }
        assertNotNull(implementingClass, "Should find class implementing interface")

        assertTrue(implementingClass.implementedInterfaces.isNotEmpty(), "Class should implement interface")
    }

    @Test
    fun testGenericClass() {
        val file = loadSourceFile("/samples/source/classes/GenericClass.ts")

        val genericClass = file.allClasses.find { it.name.contains("Generic") || it.name.contains("Box") }
        assertNotNull(genericClass, "Should find generic class")

        // Generic classes should have type parameters
        assertTrue(genericClass.typeParameters.isNotEmpty(), "Generic class should have type parameters")
    }

    @Test
    fun testAccessModifiers() {
        val file = loadSourceFile("/samples/source/classes/AccessModifiers.ts")

        // File contains User class with different access modifiers:
        // private username, protected email, public name
        val userClass = file.allClasses.find { it.name == "User" }
        assertNotNull(userClass, "Should find User class")

        // User class should have 3 fields with different access modifiers
        assertTrue(
            userClass.fields.size >= 3,
            "User class should have at least 3 fields, found: ${userClass.fields.size}"
        )

        // Check for specific fields
        val usernameField = userClass.fields.find { it.name == "username" }
        assertNotNull(usernameField, "Should find username field (private)")

        val emailField = userClass.fields.find { it.name == "email" }
        assertNotNull(emailField, "Should find email field (protected)")

        val nameField = userClass.fields.find { it.name == "name" }
        assertNotNull(nameField, "Should find name field (public)")

        // Log all fields with their modifiers for debugging
        userClass.fields.forEach { field ->
            logger.info { "Field: ${field.name}, public=${field.modifiers.isPublic}, private=${field.modifiers.isPrivate}, protected=${field.modifiers.isProtected}" }
        }

        // Count fields by access level
        val publicFields = userClass.fields.filter { it.modifiers.isPublic }
        val privateFields = userClass.fields.filter { it.modifiers.isPrivate }
        val protectedFields = userClass.fields.filter { it.modifiers.isProtected }

        logger.info { "Access modifier counts: public=${publicFields.size}, private=${privateFields.size}, protected=${protectedFields.size}" }

        // Should have at least one field with non-default modifier
        // (Note: TypeScript defaults to public if not specified, but explicit modifiers should be preserved)
        val hasExplicitModifiers = privateFields.isNotEmpty() || protectedFields.isNotEmpty()
        assertTrue(
            hasExplicitModifiers,
            "User class should have fields with explicit access modifiers (public: ${publicFields.size}, private: ${privateFields.size}, protected: ${protectedFields.size})"
        )
    }

    @Test
    fun testNestedInitializer() {
        val file = loadSourceFile("/samples/source/classes/NestedInitializer.ts")

        // File contains Bar and Foo classes
        // Foo has: bar: Bar = new Bar(); (nested object initialization)
        val barClass = file.allClasses.find { it.name == "Bar" }
        assertNotNull(barClass, "Should find Bar class")

        val fooClass = file.allClasses.find { it.name == "Foo" }
        assertNotNull(fooClass, "Should find Foo class")

        // Foo should have a field 'bar' of type Bar
        val barField = fooClass.fields.find { it.name == "bar" }
        assertNotNull(barField, "Foo should have bar field with nested initializer")

        // Verify constructor has initialization logic for the nested object
        val constructor = fooClass.ctor
        assertNotNull(constructor, "Foo should have constructor")

        logMethodDetails(constructor)
        assertTrue(constructor.cfg.stmts.isNotEmpty(), "Constructor should have statements for field initialization")
    }

    @Test
    fun testSingletonClass() {
        val file = loadSourceFile("/samples/source/classes/SingletonClass.ts")

        // File contains Logger class which implements singleton pattern
        val singletonClass = file.allClasses.find { it.name == "Logger" }
        assertNotNull(singletonClass, "Should find Logger class (singleton pattern)")

        // Singleton has private static instance field
        val hasStaticInstance = singletonClass.fields.any {
            it.modifiers.isStatic && it.name == "instance"
        }
        assertTrue(hasStaticInstance, "Singleton should have static instance field")

        // Singleton has static getInstance method
        val hasGetInstance = singletonClass.methods.any {
            it.name == "getInstance" && it.modifiers.isStatic
        }
        assertTrue(hasGetInstance, "Singleton should have static getInstance method")

        // Constructor should be private
        val constructor = singletonClass.ctor
        assertNotNull(constructor, "Singleton should have constructor")
        assertTrue(constructor.modifiers.isPrivate, "Singleton constructor should be private")
    }
}
