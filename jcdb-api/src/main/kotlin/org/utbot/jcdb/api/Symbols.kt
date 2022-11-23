package org.utbot.jcdb.api

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
    val hash: String //cvc

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
}

