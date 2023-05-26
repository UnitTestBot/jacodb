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

package org.jacodb.api.analysis

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcField
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcLocal

interface JcPointsToAnalysis<Context> {

    /** @return set of objects pointed to by variable [local] in context [context].  */
    fun reachingObjects(local: JcLocal): JcPointsToSet = reachingObjects(local, null)
    fun reachingObjects(local: JcLocal, context: Context?): JcPointsToSet

    /** @return set of objects pointed to by field  */
    fun reachingObjects(field: JcField): JcPointsToSet

    /**
     * @return set of objects pointed to by instance field f of the objects in the PointsToSet [set].
     */
    fun reachingObjects(set: JcPointsToSet, field: JcField): JcPointsToSet

    /**
     * @return the set of objects pointed to by instance field [field] of the objects pointed to by [local] in context [context].
     */
    fun reachingObjects(local: JcLocal, field: JcField): JcPointsToSet = reachingObjects(local, field, null)
    fun reachingObjects(local: JcLocal, field: JcField, context: Context? = null): JcPointsToSet

    /**
     * @return the set of objects pointed to by elements of the arrays in the PointsToSet [set].
     */
    fun reachingObjectsOfArrayElement(set: JcPointsToSet): JcPointsToSet

}

//TODO check api for consistency
interface JcPointsToSet {

    val isEmpty: Boolean

    fun intersects(other: JcPointsToSet): Boolean

    /** all possible runtime types of objects in the set.  */
    val possibleTypes: Set<JcType>

    /**
     * If this points-to set consists entirely of string constants, returns a set of these constant strings.
     *
     * If this point-to set may contain something other than constant strings, returns null.
     */
    val possibleStrings: Set<String>?

    /**
     * If this points-to set consists entirely of objects of type java.lang.Class of a known class, returns a set of these constant strings.
     *
     * If this point-to set may contain something else, returns null.
     */
    val possibleClasses: Set<JcClassOrInterface>?

}