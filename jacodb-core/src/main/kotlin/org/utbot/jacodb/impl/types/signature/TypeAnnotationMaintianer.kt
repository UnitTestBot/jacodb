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

package org.utbot.jacodb.impl.types.signature

import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.impl.bytecode.JcAnnotationImpl
import org.utbot.jacodb.impl.types.AnnotationInfo

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


// TODO: carefully recalc curTypePathStep as it may be increased by more than one
internal fun JvmType.relaxWithAnnotations(annotationInfos: List<AnnotationInfo>, cp: JcClasspath, curTypePathStep: Int): JvmType {
    val curAnnotations = annotationInfos
    val innerAnnotations = listOf<AnnotationInfo>()
    val applied = copyWith(isNullable, annotations + curAnnotations.map { JcAnnotationImpl(it, cp) })
    return if (innerAnnotations.isEmpty())
        applied
    else
        applied.relaxWithAnnotations(innerAnnotations, cp, curTypePathStep + 1)
}