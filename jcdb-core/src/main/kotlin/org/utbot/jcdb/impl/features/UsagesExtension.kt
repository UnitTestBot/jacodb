package org.utbot.jcdb.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.objectweb.asm.Opcodes
import org.utbot.jcdb.api.*
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import kotlin.streams.asStream

/**
 * find all methods that directly modifies field
 *
 * @param field field
 * @param mode mode of search
 */
suspend fun JcClasspath.findUsages(field: JcField, mode: FieldUsageMode): Sequence<JcMethod> {
    val maybeHierarchy = maybeHierarchy(field.enclosingClass, field.isPrivate) {
        it.findFieldOrNull(field.name).let {
            it == null || !it.isOverriddenBy(field)
        } // no overrides
    }
    val isStatic = field.isStatic
    val opcode = when {
        isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
        !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
        isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
        !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
        else -> return emptySequence()
    }

    return findMatches(maybeHierarchy, field = field, opcodes = listOf(opcode))
}

fun JcClasspath.asyncFindUsage(field: JcField, mode: FieldUsageMode) =
    GlobalScope.launch { findUsages(field, mode).asStream() }

fun JcClasspath.asyncFindUsage(method: JcMethod) = GlobalScope.launch { findUsages(method).asStream() }

/**
 * find all methods that call this method
 *
 * @param method method
 * @param mode mode of search
 */
suspend fun JcClasspath.findUsages(method: JcMethod): Sequence<JcMethod> {
    val maybeHierarchy = maybeHierarchy(method.enclosingClass, method.isPrivate) {
        it.findMethodOrNull(method.name, method.description).let {
            it == null || !it.isOverriddenBy(method)
        } // no overrides// no override
    }

    val opcodes = when (method.isStatic) {
        true -> setOf(Opcodes.INVOKESTATIC)
        else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
    }
    return findMatches(maybeHierarchy, method = method, opcodes = opcodes)
}

private suspend fun JcClasspath.maybeHierarchy(
    enclosingClass: JcClassOrInterface,
    private: Boolean,
    matcher: (JcClassOrInterface) -> Boolean
): Set<JcClassOrInterface> {
    return when {
        private -> hashSetOf(enclosingClass)
        else -> hierarchyExt().findSubClasses(enclosingClass.name, true).filter(matcher).toHashSet() + enclosingClass
    }
}

private suspend fun JcClasspath.findMatches(
    hierarchy: Set<JcClassOrInterface>,
    method: JcMethod? = null,
    field: JcField? = null,
    opcodes: Collection<Int>
): Sequence<JcMethod> {
    db.awaitBackgroundJobs()
    val list = hierarchy.map {
        query(
            Usages, UsageFeatureRequest(
                methodName = method?.name,
                description = method?.description,
                field = field?.name,
                opcodes = opcodes,
                className = it.name
            )
        ).flatMap {
            JcClassOrInterfaceImpl(
                this,
                it.source
            ).declaredMethods.filterIndexed { index, jcMethod -> it.offsets.contains(index) }
        }
    }

    return sequence {
        list.forEach {
            yieldAll(it)
        }
    }
}


private fun JcField.isOverriddenBy(field: JcField): Boolean {
    if (name == field.name) {
        return when {
            isPrivate -> false
            isPackagePrivate -> enclosingClass.packageName == field.enclosingClass.packageName
            else -> true
        }
    }
    return false
}

private fun JcMethod.isOverriddenBy(method: JcMethod): Boolean {
    if (name == method.name && description == method.description) {
        return when {
            isPrivate -> false
            isPackagePrivate -> enclosingClass.packageName == method.enclosingClass.packageName
            else -> true
        }
    }
    return false
}
