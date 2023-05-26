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

import org.jacodb.api.JcClasspathFeature
import org.jacodb.api.JcFeatureEvent

class JcFeaturesChain(val features: List<JcClasspathFeature>) {

    fun newRequest(vararg input: Any) = JcFeaturesRequest(features, arrayOf(*input))

}

class JcFeaturesRequest(val features: List<JcClasspathFeature>, val input: Array<Any>) {

    inline fun <reified T : JcClasspathFeature, W> call(call: (T) -> W?): W? {
        var result: W? = null
        var event: JcFeatureEvent? = null
        for (feature in features) {
            if (feature is T) {
                result = call(feature)
                if (result != null) {
                    event = feature.event(result, input)
                    break
                }
            }
        }
        if (result != null && event != null) {
            for (feature in features) {
                feature.on(event)
            }
        }
        return result
    }

    inline fun <reified T : JcClasspathFeature> run(call: (T) -> Unit) {
        for (feature in features) {
            if (feature is T) {
                call(feature)
            }
        }

    }

}


class JcFeatureEventImpl(
    override val feature: JcClasspathFeature,
    override val result: Any,
    override val input: Array<Any>
) : JcFeatureEvent