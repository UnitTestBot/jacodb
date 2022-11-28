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
    fun findSubClasses(name: String, allHierarchy: Boolean): Sequence<JcClassOrInterface>

    /**
     * find all subclasses or implementations if name points to interface. If [allHierarchy] is true then search
     * will be done recursively
     *
     * @return list with unique ClassId
     */
    fun findSubClasses(jcClass: JcClassOrInterface, allHierarchy: Boolean): Sequence<JcClassOrInterface>

    /**
     * find overrides of current method
     * @return list with unique methods overriding current
     */
    fun findOverrides(jcMethod: JcMethod): Sequence<JcMethod>

}