package org.utbot.jcdb.impl.index

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.utbot.jcdb.api.*

class UsagesExtension(
    private val db: JCDB,
    private val cp: JcClasspath
) {
    /**
     * find all methods that directly modifies field
     *
     * @param field field
     * @param mode mode of search
     */
    suspend fun findUsages(field: JcField, mode: FieldUsageMode): List<JcMethod> {
        val name = field.name
        val className = field.jcClass.name

        val maybeHierarchy = when {
            field.isPrivate -> hashSetOf(field.jcClass)
            else -> cp.findSubClasses(className, true).toHashSet() + field.jcClass
        }

        val potentialCandidates = maybeHierarchy.findPotentialCandidates(field = field.name) + field.jcClass

        val isStatic = field.isStatic
        val opcode = when {
            isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
            !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
            isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
            !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
            else -> return emptyList()
        }
        return potentialCandidates.findUsages(maybeHierarchy) { inst, hierarchyNames ->
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
    suspend fun findUsages(method: JcMethod): List<JcMethod> {
        val name = method.name
        val className = method.jcClass.name
        val maybeHierarchy = when {
            method.isPrivate -> hashSetOf(method.jcClass)
            else -> cp.findSubClasses(className, true).toHashSet() + method.jcClass
        }

        val potentialCandidates = maybeHierarchy.findPotentialCandidates(method = method.name) + method.jcClass
        val opcodes = when (method.isStatic) {
            true -> setOf(Opcodes.INVOKESTATIC)
            else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
        }
        return potentialCandidates.findUsages(maybeHierarchy) { inst, hierarchyNames ->
            inst is MethodInsnNode
                    && inst.name == name
                    && opcodes.contains(inst.opcode)
                    && hierarchyNames.contains(Type.getObjectType(inst.owner).className)
        }
    }

    private suspend fun Set<JcClassOrInterface>.findUsages(
        hierarchy: Set<JcClassOrInterface>,
        matcher: (AbstractInsnNode, Set<String>) -> Boolean
    ): List<JcMethod> {
        val result = hashSetOf<JcMethod>()
        val hierarchyNames = hierarchy.map { it.name }.toSet()
        forEach {
            val jcClass = cp.findClassOrNull(it.name)
            val asm = jcClass?.bytecode()
            asm?.methods?.forEach { method ->
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


    private suspend fun Set<JcClassOrInterface>.findPotentialCandidates(
        method: String? = null,
        field: String? = null
    ): Set<JcClassOrInterface> {
        db.awaitBackgroundJobs()

        return flatMap { classId ->
            val classNames = cp.query<String, UsageIndexRequest>(
                Usages.key, UsageIndexRequest(
                    method = method,
                    field = field,
                    className = classId.name
                )
            ).toList()
            classNames.mapNotNull { cp.findClassOrNull(it) }
        }.toSet()
    }
}


val JcClasspath.reversedUsagesExt: UsagesExtension
    get() {
        return UsagesExtension(db, this)
    }