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

// TODO: consider 'SingletonChunk'
data object SingletonChunk : Chunk

val SingletonChunkStrategy = ChunkStrategy<JcInst> {
    SingletonChunk
}

data class MethodChunk(
    val method: JcMethod,
) : Chunk

val MethodChunkStrategy = ChunkStrategy<JcInst> { stmt ->
    MethodChunk(stmt.location.method)
}

data class ClassChunk(
    val method: JcClassOrInterface,
) : Chunk

val ClassChunkStrategy = ChunkStrategy<JcInst> { stmt ->
    val jcClass = stmt.location.method.enclosingClass
    ClassChunk(jcClass)
}

val ClassWithNestedChunkStrategy = ChunkStrategy<JcInst> { stmt ->
    val jClass = generateSequence(stmt.location.method.enclosingClass) { it.outerClass }
        .last()
    ClassChunk(jClass)
}

data class PackageChunk(
    val packageName: String,
) : Chunk

val PackageChunkStrategy = ChunkStrategy<JcInst> { stmt ->
    PackageChunk(stmt.location.method.enclosingClass.packageName)
}
