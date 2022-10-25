package org.utbot.jcdb.impl.index

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.utbot.jcdb.api.*

/**
 * find all methods that directly modifies field
 *
 * @param field field
 * @param mode mode of search
 */
suspend fun JcClasspath.findUsages(field: JcField, mode: FieldUsageMode): List<JcMethod> {
    val name = field.name
    val className = field.enclosingClass.name

    val maybeHierarchy = when {
        field.isPrivate -> hashSetOf(field.enclosingClass)
        else -> hierarchyExt().findSubClasses(className, true).toHashSet() + field.enclosingClass
    }

    val potentialCandidates = findPotentialCandidates(maybeHierarchy, field = field.name) + field.enclosingClass

    val isStatic = field.isStatic
    val opcode = when {
        isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
        !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
        isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
        !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
        else -> return emptyList()
    }
    return findUsages(potentialCandidates) { inst, hierarchyNames ->
        inst is FieldInsnNode
                && inst.name == name
                && inst.opcode == opcode
                && hierarchyNames.contains(Type.getObjectType(inst.owner).className)
    }
}

/**
 * find all methods that call this method
 *
 * @param method method
 * @param mode mode of search
 */
suspend fun JcClasspath.findUsages(method: JcMethod): List<JcMethod> {
    val name = method.name
    val className = method.enclosingClass.name
    val maybeHierarchy = when {
        method.isPrivate -> hashSetOf(method.enclosingClass)
        else -> hierarchyExt().findSubClasses(className, true).toHashSet() + method.enclosingClass
    }

    val potentialCandidates = findPotentialCandidates(maybeHierarchy, method = method.name) + method.enclosingClass
    val opcodes = when (method.isStatic) {
        true -> setOf(Opcodes.INVOKESTATIC)
        else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
    }
    return findUsages(potentialCandidates) { inst, hierarchyNames ->
        inst is MethodInsnNode
                && inst.name == name
                && opcodes.contains(inst.opcode)
                && hierarchyNames.contains(Type.getObjectType(inst.owner).className)
    }
}

private fun findUsages(
    hierarchy: Set<JcClassOrInterface>,
    matcher: (AbstractInsnNode, Set<String>) -> Boolean
): List<JcMethod> {
    val result = hashSetOf<JcMethod>()
    val hierarchyNames = hierarchy.map { it.name }.toSet()
    hierarchy.forEach { jcClass ->
        val asm = jcClass.bytecode()
        asm.methods?.forEach { method ->
            for (inst in method.instructions) {
                val matches = matcher(inst, hierarchyNames)
                if (matches) {
                    val methodId = jcClass.methods.firstOrNull {
                        it.name == method.name && it.description == method.desc
                    }
                    if (methodId != null) {
                        result.add(methodId)
                    }
                    break
                }
            }
        }
    }
    return result.toList()
}


private suspend fun JcClasspath.findPotentialCandidates(
    hierarchy: Set<JcClassOrInterface>,
    method: String? = null,
    field: String? = null
): Set<JcClassOrInterface> {
    db.awaitBackgroundJobs()

    return hierarchy.flatMap { jcClass ->
        val classNames = query(
            Usages, UsageIndexRequest(
                method = method,
                field = field,
                className = jcClass.name
            )
        ).toList()
        classNames.mapNotNull { findClassOrNull(it) }
    }.toSet()
}


