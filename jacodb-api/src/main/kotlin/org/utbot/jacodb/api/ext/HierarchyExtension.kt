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

package org.utbot.jacodb.api.ext

import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcMethod

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
    fun findOverrides(jcMethod: JcMethod, includeAbstract: Boolean = true): Sequence<JcMethod>

}