package org.utbot.jcdb.impl.index

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.utbot.jcdb.api.*

class ReversedUsagesExtension(
    private val db: JCDB,
    private val cp: JcClasspath
) {
    /**
     * find all methods that directly modifies field
     *
     * @param fieldId field
     * @param mode mode of search
     */
    suspend fun findUsages(fieldId: JcField, mode: FieldUsageMode): List<JcMethod> {
        val name = fieldId.name
        val className = fieldId.jcClass.name

        val maybeHierarchy = when {
            fieldId.isPrivate -> hashSetOf(fieldId.jcClass)
            else -> cp.findSubClasses(className, true).toHashSet()
        }

        val potentialCandidates = maybeHierarchy.findPotentialCandidates(field = fieldId.name)

        val isStatic = fieldId.isStatic
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
     * @param methodId method
     * @param mode mode of search
     */
    suspend fun findUsages(methodId: JcMethod): List<JcMethod> {
        val name = methodId.name
        val className = methodId.jcClass.name

        val hierarchy = cp.findSubClasses(className, true).toHashSet() + methodId.jcClass

        val potentialCandidates = hierarchy.findPotentialCandidates(method = methodId.name)
        val opcodes = when (methodId.isStatic) {
            true -> setOf(Opcodes.INVOKESTATIC)
            else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
        }
        return potentialCandidates.findUsages(hierarchy) { inst, hierarchyNames ->
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
            val classId = cp.findClassOrNull(it.name)
            val asm = classId?.bytecode()
            asm?.methods?.forEach { method ->
                for (inst in method.instructions) {
                    val matches = matcher(inst, hierarchyNames)
                    if (matches) {
                        val methodId = classId.methods.firstOrNull {
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


val JcClasspath.reversedUsagesExt: ReversedUsagesExtension
    get() {
        return ReversedUsagesExtension(db, this)
    }