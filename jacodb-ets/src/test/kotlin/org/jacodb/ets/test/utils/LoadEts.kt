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

package org.jacodb.ets.test.utils

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.model.EtsFile

fun loadEtsFile(filePath: String): EtsFile {
    val sampleFilePath = object {}::class.java.getResourceAsStream(filePath)
        ?: error("Resource not found: $filePath")
    val etsFileDto = EtsFileDto.loadFromJson(sampleFilePath)
    return convertToEtsFile(etsFileDto)
}

fun loadDto(filePath: String): EtsFileDto {
    val sampleFilePath = object {}::class.java.getResourceAsStream(filePath)
        ?: error("Resource not found: $filePath")
    return EtsFileDto.loadFromJson(sampleFilePath)
}
