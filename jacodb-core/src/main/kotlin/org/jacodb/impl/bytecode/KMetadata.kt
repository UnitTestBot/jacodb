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

package org.jacodb.impl.bytecode

import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmType
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.signature
import mu.KLogging
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.impl.features.classpaths.KotlinMetadata
import org.jacodb.impl.features.classpaths.KotlinMetadataHolder

val logger = object : KLogging() {}.logger

val JcClassOrInterface.kMetadata: KotlinMetadataHolder?
    get() {
        return extensionValue(KotlinMetadata.METADATA_KEY)
    }

val JcMethod.kmFunction: KmFunction?
    get() =
        enclosingClass.kMetadata?.functions?.firstOrNull { it.signature?.name == name && it.signature?.descriptor == description }

val JcMethod.kmConstructor: KmConstructor?
    get() =
        enclosingClass.kMetadata?.constructors?.firstOrNull { it.signature?.name == name && it.signature?.descriptor == description }

val JcParameter.kmParameter: KmValueParameter?
    get() {
        try {
            method.kmFunction?.let {
                // Shift needed to properly handle extension functions
                val shift = if (it.receiverParameterType != null) 1 else 0

                // index - shift could be out of bounds if generated JVM parameter is fictive
                // E.g., see how extension functions and coroutines are compiled
                return it.valueParameters.getOrNull(index - shift)
            }

            return method.kmConstructor?.valueParameters?.get(index)
        } catch (e: Exception) {
            return null
        }
    }

// If parameter is a receiver parameter, it doesn't have KmValueParameter instance, but we still can get KmType for it
val JcParameter.kmType: KmType?
    get() =
        kmParameter?.type ?: run {
            if (index == 0)
                method.kmFunction?.receiverParameterType
            else
                null
        }

val JcField.kmType: KmType?
    get() =
        enclosingClass.kMetadata?.properties?.let { property ->
            // TODO: maybe we need to check desc here as well
            property.firstOrNull { it.fieldSignature?.name == name }?.returnType
        }

val JcMethod.kmReturnType: KmType?
    get() =
        kmFunction?.returnType
