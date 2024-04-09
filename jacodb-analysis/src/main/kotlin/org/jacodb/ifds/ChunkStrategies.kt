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

package org.jacodb.ifds

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.packageName
import org.jacodb.ifds.domain.Chunk

data object SingleChunk : Chunk

val singleChunkStrategy = ChunkStrategy<JcInst> {
    SingleChunk
}

data class MethodChunk(
    val method: JcMethod,
) : Chunk

val MethodChunkStrategy = ChunkStrategy<JcInst> {
    MethodChunk(it.location.method)
}

data class ClassChunk(
    val method: JcClassOrInterface,
) : Chunk

val classChunkStrategy = ChunkStrategy<JcInst> {
    val jcClass = it.location.method.enclosingClass
    ClassChunk(jcClass)
}

val classWithNestedChunkStrategy = ChunkStrategy<JcInst> {
    val jClass = generateSequence(it.location.method.enclosingClass) { it.outerClass }
        .last()
    ClassChunk(jClass)
}

data class PackageChunk(
    val packageName: String,
) : Chunk

val packageChunkStrategy = ChunkStrategy<JcInst> {
    PackageChunk(it.location.method.enclosingClass.packageName)
}