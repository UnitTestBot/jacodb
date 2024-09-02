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

package org.jacodb.impl.features

import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcFeatureEvent
import org.jacodb.api.jvm.JcLookupExtFeature

class JcFeaturesChain(val features: List<JcClasspathFeature>) {

    val featuresArray = features.toTypedArray()
    val classLookups = features.filterIsInstance<JcLookupExtFeature>()

    inline fun <reified T : JcClasspathFeature> run(call: (T) -> Unit) {
        for (feature in featuresArray) {
            if (feature is T) {
                call(feature)
            }
        }
    }

    inline fun <reified T : JcClasspathFeature, W> call(call: (T) -> W?): W? {
        for (feature in featuresArray) {
            if (feature is T) {
                val result = call(feature)
                if (result != null) {
                    val event = feature.event(result)
                    if (event != null) {
                        for (anyFeature in featuresArray) {
                            anyFeature.on(event)
                        }
                    }
                    return result
                }
            }
        }
        return null
    }
}

class JcFeatureEventImpl(
    override val feature: JcClasspathFeature,
    override val result: Any,
) : JcFeatureEvent
