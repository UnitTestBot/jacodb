package org.utbot.jcdb.api.ext

import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.NoClassInClasspathException
import org.utbot.jcdb.api.findFieldOrNull
import org.utbot.jcdb.api.findMethodOrNull
import org.utbot.jcdb.api.throwClassNotFound

/**
 * find all methods used in bytecode of specified `method`
 * @param method method to analyze
 */
suspend fun JcClasspath.findMethodsUsedIn(method: JcMethod): List<JcMethod> {
    val methodNode = method.body() ?: return emptyList()
    val result = LinkedHashSet<JcMethod>()
    methodNode.instructions.forEach { instruction ->
        when (instruction) {
            is MethodInsnNode -> {
                val owner = Type.getObjectType(instruction.owner).className
                val clazz = findClassOrNull(owner)
                if (clazz != null) {
                    clazz.findMethodOrNull(instruction.name, instruction.desc)?.also {
                        result.add(it)
                    }
                }
            }
        }
    }
    return result.toImmutableList()
}

class FieldUsagesResult(
    val reads: List<JcField>,
    val writes: List<JcField>
) {
    companion object {
        val EMPTY = FieldUsagesResult(emptyList(), emptyList())
    }
}

/**
 * find all methods used in bytecode of specified `method`
 * @param method method to analyze
 */
suspend fun JcClasspath.findFieldsUsedIn(method: JcMethod): FieldUsagesResult {
    val methodNode = method.body() ?: return FieldUsagesResult.EMPTY
    val reads = LinkedHashSet<JcField>()
    val writes = LinkedHashSet<JcField>()
    methodNode.instructions.forEach { instruction ->
        when (instruction) {
            is FieldInsnNode -> {
                val owner = Type.getObjectType(instruction.owner).className
                val clazz = findClassOrNull(owner)
                if (clazz != null) {
                    clazz.findFieldOrNull(instruction.name)?.also {
                        when (instruction.opcode) {
                            Opcodes.GETFIELD -> reads.add(it)
                            Opcodes.GETSTATIC -> reads.add(it)
                            Opcodes.PUTFIELD -> writes.add(it)
                            Opcodes.PUTSTATIC -> writes.add(it)
                        }
                    }
                }
            }
        }
    }
    return FieldUsagesResult(
        reads = reads.toImmutableList(),
        writes = writes.toImmutableList()
    )

}


suspend inline fun <reified T> JcClasspath.findClassOrNull(): JcClassOrInterface? {
    return findClassOrNull(T::class.java.name)
}

suspend inline fun <reified T> JcClasspath.findTypeOrNull(): JcType? {
    return findClassOrNull(T::class.java.name)?.let {
        typeOf(it)
    }
}


/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
suspend fun JcClasspath.findClass(name: String): JcClassOrInterface {
    return findClassOrNull(name) ?: name.throwClassNotFound()
}

/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
suspend inline fun <reified T> JcClasspath.findClass(): JcClassOrInterface {
    return findClassOrNull<T>() ?: throwClassNotFound<T>()
}

suspend inline fun <reified T> JcClasspath.findSubClasses(): List<JcClassOrInterface> {
    return findSubClasses(T::class.java.name)
}
