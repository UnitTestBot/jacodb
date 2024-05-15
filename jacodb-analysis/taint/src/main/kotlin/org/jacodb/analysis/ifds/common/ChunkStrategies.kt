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

package org.jacodb.analysis.ifds.common

import org.jacodb.analysis.ifds.domain.Chunk
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.packageName

data object SingletonChunk : Chunk

val SingletonChunkStrategy = org.jacodb.analysis.ifds.ChunkStrategy<JcInst> {
    SingletonChunk
}

data class MethodChunk(
    val method: JcMethod,
) : Chunk

val MethodChunkStrategy = org.jacodb.analysis.ifds.ChunkStrategy<JcInst> { stmt ->
    MethodChunk(stmt.location.method)
}

data class ClassChunk(
    val jcClass: JcClassOrInterface,
) : Chunk

val ClassChunkStrategy = org.jacodb.analysis.ifds.ChunkStrategy<JcInst> { stmt ->
    val jcClass = stmt.location.method.enclosingClass
    ClassChunk(jcClass)
}

val ClassWithNestedChunkStrategy = org.jacodb.analysis.ifds.ChunkStrategy<JcInst> { stmt ->
    val jClass = generateSequence(stmt.location.method.enclosingClass) { it.outerClass }
        .last()
    ClassChunk(jClass)
}

data class PackageChunk(
    val packageName: String,
) : Chunk

val PackageChunkStrategy = org.jacodb.analysis.ifds.ChunkStrategy<JcInst> { stmt ->
    PackageChunk(stmt.location.method.enclosingClass.packageName)
}
