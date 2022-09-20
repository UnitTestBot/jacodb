package org.utbot.jcdb.api.ext

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcMethod

/**
 * hierarchy extension for classpath
 */
interface HierarchyExtension {
    /**
     * find all subclasses or implementations if name points to interface. If [allHierarchy] is true then search
     * will be done recursively
     *
     * @return list with unique ClassId
     */
    suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<JcClassOrInterface>

    /**
     * find all subclasses or implementations if name points to interface. If [allHierarchy] is true then search
     * will be done recursively
     *
     * @return list with unique ClassId
     */
    suspend fun findSubClasses(classId: JcClassOrInterface, allHierarchy: Boolean): List<JcClassOrInterface>

    /**
     * find overrides of current method
     * @return list with unique methods overriding current
     */
    suspend fun findOverrides(methodId: JcMethod): List<JcMethod>

}