package org.utbot.jcdb.api.ext

import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.MethodId

interface HierarchyExtension {
    suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId>

    suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId>

    suspend fun findOverrides(methodId: MethodId): List<MethodId>

    suspend fun Collection<ByteCodeLocation>.subClasses(name: String, allHierarchy: Boolean): List<String>
}