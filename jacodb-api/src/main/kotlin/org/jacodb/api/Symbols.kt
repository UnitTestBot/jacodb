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

package org.jacodb.api

import org.objectweb.asm.Opcodes
import java.io.File


/**
 * Immutable structure represented a file system location of bytecode such as `jar` or build folder.
 *
 * Each location points to file and has `hash` which may be used as unique identifier of location.
 * Calculation of such `id` may include file system operation and may be expensive.
 * For optimization this class uses `cachedId` calculated at the time when instance of this
 * class is created and `currentId` i.e id calculated the same way as when this method
 *
 */
interface JcByteCodeLocation {
    val jarOrFolder: File
    val fileSystemId: String //id based on from file system

    val type: LocationType

    /** url for bytecode location */
    val path: String

    /**
     * this operation may involve file-system operations and may be expensive
     *
     * @returns true if file-system has changes not reflected in current `location`
     */
    fun isChanged(): Boolean

    /**
     * @return new refreshed version of this `location`
     */
    fun createRefreshed(): JcByteCodeLocation?

    /**
     * resolve byte-code based on class name
     *
     * @param classFullName full name of the class to be resolved
     * @return input stream with byte-code or null if class is not found in this location
     */
    fun resolve(classFullName: String): ByteArray?

    val classes: Map<String, ByteArray>?
    val classNames: Set<String>?

}

interface JcDeclaration {
    val location: RegisteredLocation
    val relativePath: String // relative to `location` path for declaration
}

interface JcSymbol {
    val name: String
}

interface JcAnnotated {
    val annotations: List<JcAnnotation>
    val declaration: JcDeclaration
}

interface JcAnnotatedSymbol : JcSymbol, JcAnnotated

/**
 * represents structure which has access modifiers like field, class, method
 */
interface JcAccessible {

    /** byte-code access value */
    val access: Int

    val isPublic: Boolean
        get() {
            return access and Opcodes.ACC_PUBLIC != 0
        }

    /**
     * is item has `private` modifier
     */
    val isPrivate: Boolean
        get() {
            return access and Opcodes.ACC_PRIVATE != 0
        }

    /**
     * is item has `protected` modifier
     */
    val isProtected: Boolean
        get() {
            return access and Opcodes.ACC_PROTECTED != 0
        }

    /**
     * is item has `package` modifier
     */
    val isPackagePrivate: Boolean
        get() {
            return !isPublic && !isProtected && !isPrivate
        }

    /**
     * is item has `static` modifier
     */
    val isStatic: Boolean
        get() {
            return access and Opcodes.ACC_STATIC != 0
        }

    /**
     * is item has `final` modifier
     */
    val isFinal: Boolean
        get() {
            return access and Opcodes.ACC_FINAL != 0
        }

    /**
     * is item has `abstract` modifier
     */
    val isAbstract: Boolean
        get() {
            return access and Opcodes.ACC_ABSTRACT != 0
        }

    val isSynthetic: Boolean
        get() {
            return access and Opcodes.ACC_SYNTHETIC != 0
        }

}

