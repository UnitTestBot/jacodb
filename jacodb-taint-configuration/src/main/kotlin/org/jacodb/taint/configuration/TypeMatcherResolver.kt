package org.jacodb.taint.configuration

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.short

fun TypeMatcher.resolveTypeMatcherCondition(
    cp: JcClasspath,
    nameMatches: (NameMatcher, String) -> Boolean
): (Position) -> Condition {
    if (this is AnyTypeMatcher) {
        return { ConstantTrue }
    }

    if (this is JcTypeNameMatcher) {
        val type = cp.findTypeOrNull(typeName) ?: return { ConstantTrue }
        return { pos: Position -> TypeMatches(pos, type) }
    }

    if (this is PrimitiveNameMatcher) {
        val types = primitiveTypes(cp).filter { name == it.typeName }
        return { pos: Position -> mkOr(types.map { TypeMatches(pos, it) }) }
    }

    val typeMatchers = (this as ClassMatcher).extractAlternatives()
    val unresolvedMatchers = mutableListOf<ClassMatcher>()
    val types = mutableListOf<JcType>()

    for (matcher in typeMatchers) {
        val pkgMatcher = matcher.pkg
        val clsMatcher = matcher.classNameMatcher

        if (pkgMatcher !is NameExactMatcher || clsMatcher !is NameExactMatcher) {
            unresolvedMatchers += matcher
            continue
        }

        val type = cp.findTypeOrNull("${pkgMatcher.name}$DOT_DELIMITER${clsMatcher.name}")
            ?: continue

        types.add(type)
    }

    if (unresolvedMatchers.isNotEmpty()) {
        val allClassNames = cp.registeredLocations.flatMapTo(hashSetOf()) {
            val names = it.jcLocation?.classNames ?: return@flatMapTo emptyList()
            names.map { name ->
                val packageName = name.substringBeforeLast(DOT_DELIMITER, missingDelimiterValue = "")
                val simpleName = name.substringAfterLast(DOT_DELIMITER)
                packageName to simpleName
            }
        }

        unresolvedMatchers.forEach { classMatcher ->
            val matchedClassNames = allClassNames.filter { (packageName, simpleName) ->
                nameMatches(classMatcher.pkg, packageName) && nameMatches(classMatcher.classNameMatcher, simpleName)
            }

            matchedClassNames.mapNotNullTo(types) { (packageName, simpleName) ->
                cp.findTypeOrNull("${packageName}$DOT_DELIMITER${simpleName}")
            }
        }
    }

    return { pos: Position -> mkOr(types.map { TypeMatches(pos, it) }) }
}

private fun primitiveTypes(cp: JcClasspath): Set<JcPrimitiveType> = setOf(
    cp.boolean,
    cp.byte,
    cp.short,
    cp.int,
    cp.long,
    cp.char,
    cp.float,
    cp.double,
)
