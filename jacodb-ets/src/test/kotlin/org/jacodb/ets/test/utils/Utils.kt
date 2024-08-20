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

import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.toPath

fun getResourcePath(res: String): Path {
    require(res.startsWith("/")) { "Resource path must start with '/': '$res'" }
    return object {}::class.java.getResource(res)?.toURI()?.toPath()
        ?: error("Resource not found: '$res'")
}

fun getResourceStream(res: String): InputStream {
    require(res.startsWith("/")) { "Resource path must start with '/': '$res'" }
    return object {}::class.java.getResourceAsStream(res)
        ?: error("Resource not found: '$res'")
}
