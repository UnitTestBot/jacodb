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

package org.jacodb.api.jvm.analysis

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcLocal
import org.jacodb.api.jvm.ext.objectType

class FullObjectsSet(type: JcType) : JcPointsToSet {

    override val possibleTypes: Set<JcType> = setOf(type)

    override val isEmpty: Boolean
        get() = possibleTypes.isEmpty()


    override fun intersects(other: JcPointsToSet) = false

    override val possibleStrings: Set<String>? = null
    override val possibleClasses: Set<JcClassOrInterface>? = null
}

class PrimitivePointsAnalysis(private val classpath: JcClasspath) : JcPointsToAnalysis<JcInst> {

    override fun reachingObjects(local: JcLocal, context: JcInst?): JcPointsToSet {
        return FullObjectsSet(local.type)
    }

    override fun reachingObjects(field: JcField): JcPointsToSet {
        return FullObjectsSet(classpath.findTypeOrNull(field.type.typeName) ?: classpath.objectType)
    }

    override fun reachingObjects(set: JcPointsToSet, field: JcField): JcPointsToSet {
        return reachingObjects(field)
    }

    override fun reachingObjects(local: JcLocal, field: JcField, context: JcInst?): JcPointsToSet {
        return reachingObjects(field)
    }

    override fun reachingObjectsOfArrayElement(set: JcPointsToSet): JcPointsToSet {
        return FullObjectsSet(classpath.objectType)
    }
}
