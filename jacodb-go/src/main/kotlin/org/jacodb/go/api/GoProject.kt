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

package org.jacodb.go.api

import org.jacodb.api.common.CommonType
import org.jacodb.api.common.Project

class File(
    val name: String,
    val base: Int,
    val size: Int,
    val lines: List<Int>,
)

class FileSet(
    val files: List<File>,
)

class GoProject(
    val methods: List<GoMethod>,
    val fileSet: FileSet,
) : Project {
    override fun close() {}

    override fun findTypeOrNull(name: String): CommonType? {
        // return class or interface or null if there is no such class found in locations
        methods.forEach {
            it.blocks.forEach { block ->
                block.insts.forEach { inst ->
                    inst.operands.forEach { expr ->
                        if (expr.toString() == name) {
                            return expr.type
                        }
                    }
                }
            }
        }
        return null
    }
}
