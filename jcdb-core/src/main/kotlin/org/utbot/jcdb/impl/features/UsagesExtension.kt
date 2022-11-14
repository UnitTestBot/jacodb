package org.utbot.jcdb.impl.features

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.utbot.jcdb.api.*
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl

/**
 * find all methods that directly modifies field
 *
 * @param field field
 * @param mode mode of search
 */
suspend fun JcClasspath.findUsages(field: JcField, mode: FieldUsageMode): List<JcMethod> {
    val maybeHierarchy = maybeHierarchy(field.enclosingClass, field.isPrivate)
    val isStatic = field.isStatic
    val opcode = when {
        isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
        !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
        isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
        !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
        else -> return emptyList()
    }

    val candidates = findMatches(
        maybeHierarchy, field = field, opcodes = listOf(opcode)
    ) + field.enclosingClass
    val name = field.name
    return findUsages(candidates) { inst, hierarchyNames ->
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
    val maybeHierarchy = maybeHierarchy(method.enclosingClass, method.isPrivate)

    val opcodes = when (method.isStatic) {
        true -> setOf(Opcodes.INVOKESTATIC)
        else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
    }
    val candidates = findMatches(maybeHierarchy, method = method, opcodes = opcodes) + method.enclosingClass
    val name = method.name
    val desc = method.description
    return findUsages(candidates) { inst, hierarchyNames ->
        inst is MethodInsnNode
                && inst.name == name
                && inst.desc == desc
                && opcodes.contains(inst.opcode)
                && hierarchyNames.contains(Type.getObjectType(inst.owner).className)
    }
}

private suspend fun JcClasspath.maybeHierarchy(
    enclosingClass: JcClassOrInterface,
    private: Boolean
): Set<JcClassOrInterface> {
    return when {
        private -> hashSetOf(enclosingClass)
        else -> hierarchyExt().findSubClasses(enclosingClass.name, true).toHashSet() + enclosingClass
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
                    val methodId = jcClass.declaredMethods.firstOrNull {
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

private suspend fun JcClasspath.findMatches(
    hierarchy: Set<JcClassOrInterface>,
    method: JcMethod? = null,
    field: JcField? = null,
    opcodes: Collection<Int>
): Set<JcClassOrInterface> {
    db.awaitBackgroundJobs()

    return hierarchy.flatMap { jcClass ->
        val classNames = query(
            Usages, UsageFeatureRequest(
                methodName = method?.name,
                methodDesc = method?.description,
                field = field?.name,
                opcodes = opcodes,
                className = jcClass.name
            )
        ).toList()
        classNames.map { JcClassOrInterfaceImpl(this, it) }
    }.toSet()
}


