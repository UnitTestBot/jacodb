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

package org.jacodb.configuration

import kotlinx.coroutines.runBlocking
import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.jacodb.impl.features.hierarchyExt
import java.nio.file.Path
import kotlin.io.path.readText


class TaintConfigurationFeature private constructor(jsonConfig: String) : JcClasspathFeature {
    private val rulesByClass: MutableMap<JcClassOrInterface, List<SerializedTaintConfigurationItem>> = hashMapOf()
    private val rulesForMethod: MutableMap<JcMethod, List<TaintConfigurationItem>> = hashMapOf()
    private val configuration: List<SerializedTaintConfigurationItem> = jsonConfig.deserialize()
    private val compiledRegex: MutableMap<String, Regex> = hashMapOf()

    fun getConfigForMethod(method: JcMethod): List<TaintConfigurationItem> =
        resolveConfigForMethod(method)

    private var primitiveTypesSet: Set<JcPrimitiveType>? = null

    private fun primitiveTypes(method: JcMethod): Set<JcPrimitiveType> {
        if (primitiveTypesSet == null) {
            val cp = method.enclosingClass.classpath
            primitiveTypesSet = setOf(
                cp.boolean,
                cp.byte,
                cp.short,
                cp.int,
                cp.long,
                cp.char,
                cp.float,
                cp.double,
            )
        }
        return primitiveTypesSet!!
    }


    private fun resolveConfigForMethod(method: JcMethod): List<TaintConfigurationItem> {
        val taintConfigurationItems = rulesForMethod[method]
        if (taintConfigurationItems != null) {
            return taintConfigurationItems
        }

        val classRules = getClassRules(method.enclosingClass)

        val destination = mutableListOf<TaintConfigurationItem>()

        classRules.mapNotNullTo(destination) {

            val functionMatcher = it.methodInfo

            if (!functionMatcher.matches(method)) return@mapNotNullTo null

            it.resolveForMethod(method)
        }

        method
            .enclosingClass
            .allSuperHierarchySequence
            .distinct()
            .map { getClassRules(it) }
            .forEach { rules ->
                rules.mapNotNullTo(destination) {
                    val methodInfo = it.methodInfo
                    if (!methodInfo.applyToOverrides || !methodInfo.matches(method)) return@mapNotNullTo null

                    it.resolveForMethod(method)
                }
            }

        rulesForMethod[method] = destination.distinct()

        return rulesForMethod.getValue(method)
    }

    private fun getClassRules(clazz: JcClassOrInterface) = rulesByClass.getOrPut(clazz) {
        val currentPackage = clazz.packageName

        configuration.filter {
            val functionMatcher = it.methodInfo
            val cls = functionMatcher.cls

            cls.matches(currentPackage, clazz.simpleName)
        }
    }

    private fun FunctionMatcher.matches(method: JcMethod): Boolean {
        val functionNameMatcher = functionName
        val functionName = if (method.isConstructor) "init^" else method.name
        val functionNameMatches = functionNameMatcher.matches(functionName)

        if (!functionNameMatches) return false

        val parameterMatches = parametersMatchers.all {
            val parameter = method.parameters.getOrNull(it.index) ?: return@all false
            it.typeMatcher.matches(parameter.type)
        }

        if (!parameterMatches) return false

        val returnTypeMatches = returnTypeMatcher.matches(method.returnType)

        if (!returnTypeMatches) return false

        // TODO function label?????

        require(modifier == -1) {
            "Unexpected modifier matcher value $modifier"
        }

        val isExcluded = exclude.any { it.matches(method) }

        return !isExcluded
    }

    private fun ClassMatcher.matches(fqn: String) = matches(
        fqn.substringBeforeLast(".", missingDelimiterValue = ""),
        fqn.substringAfterLast(".", missingDelimiterValue = fqn)
    )

    private fun ClassMatcher.matches(pkgName: String, className: String): Boolean {
        val packageMatches = pkg.matches(pkgName)

        if (!packageMatches) return false

        return classNameMatcher.matches(className)
    }

    private fun NameMatcher.matches(nameToBeMatched: String): Boolean = when (this) {
        AnyNameMatcher -> true
        is NameExactMatcher -> nameToBeMatched == name
        is NamePatternMatcher -> {
            compiledRegex.getOrPut(pattern) {
                pattern.toRegex()
            }.matches(nameToBeMatched)
        }
    }


    private fun TypeMatcher.matches(typeName: TypeName): Boolean = matches(typeName.typeName)

    private fun TypeMatcher.matches(typeName: String): Boolean =
        when (this) {
            AnyTypeMatcher -> true
            is ClassMatcher -> matches(typeName)
            is PrimitiveNameMatcher -> name == typeName
        }

    private val SerializedTaintConfigurationItem.methodInfo: FunctionMatcher
        get() = when (this) {
            is SerializedTaintCleaner -> methodInfo
            is SerializedTaintEntryPointSource -> methodInfo
            is SerializedTaintMethodSink -> methodInfo
            is SerializedTaintMethodSource -> methodInfo
            is SerializedTaintPassThrough -> methodInfo
        }

    private fun SerializedTaintConfigurationItem.resolveForMethod(method: JcMethod): TaintConfigurationItem =
        when (this) {
            is SerializedTaintCleaner -> TaintCleaner(method, condition.resolve(method), actionsAfter.resolve(method))
            is SerializedTaintEntryPointSource -> TaintEntryPointSource(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintMethodSink -> TaintMethodSink(condition.resolve(method), method)
            is SerializedTaintMethodSource -> TaintMethodSource(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintPassThrough -> TaintPassThrough(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )
        }

    private fun Condition.resolve(method: JcMethod): Condition = accept(ConditionSpecializer(method))
    private fun List<Action>.resolve(method: JcMethod): List<Action> = flatMap { it.accept(ActionSpecializer(method)) }


    private fun specializePosition(method: JcMethod, position: Position): List<Position> {
        if (!inBounds(method, position)) return emptyList()
        if (position !is AnyArgument) return listOf(position)
        return method.parameters.indices.map { Argument(it) }.filter { inBounds(method, it) }
    }

    private fun mkOr(conditions: List<Condition>) = if (conditions.size == 1) conditions.single() else Or(conditions)

    private fun inBounds(method: JcMethod, position: Position): Boolean =
        when (position) {
            AnyArgument -> method.parameters.isNotEmpty()
            is Argument -> position.number in method.parameters.indices
            Result -> method.returnType.typeName == PredefinedPrimitives.Void
            ThisArgument -> !method.isStatic
        }

    private inner class ActionSpecializer(val method: JcMethod) : TaintActionVisitor<List<Action>> {
        override fun visit(action: CopyAllMarks): List<Action> {
            val from = specializePosition(method, action.from)
            val to = specializePosition(method, action.to)

            return from.flatMap { fst ->
                to.mapNotNull { snd ->
                    if (fst == snd) return@mapNotNull null

                    CopyAllMarks(fst, snd)
                }
            }
        }

        override fun visit(action: CopyMark): List<Action> {
            val from = specializePosition(method, action.from)
            val to = specializePosition(method, action.to)

            return from.flatMap { fst ->
                to.mapNotNull { snd ->
                    if (fst == snd) return@mapNotNull null

                    action.copy(from = fst, to = snd)
                }
            }
        }

        override fun visit(action: AssignMark): List<Action> =
            specializePosition(method, action.position).map { action.copy(position = it) }

        override fun visit(action: RemoveAllMarks): List<Action> =
            specializePosition(method, action.position).map { action.copy(position = it) }

        override fun visit(action: RemoveMark): List<Action> =
            specializePosition(method, action.position).map { action.copy(position = it) }
    }

    private inner class ConditionSpecializer(val method: JcMethod) : ConditionVisitor<Condition> {
        override fun visit(condition: And): Condition = And(condition.conditions.map { it.accept(this) })

        override fun visit(condition: Or): Condition = Or(condition.conditions.map { it.accept(this) })

        override fun visit(condition: Not): Condition = Not(condition.condition.accept(this))

        override fun visit(condition: IsConstant): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: IsType): Condition {
            val position = specializePosition(method, condition.position)

            val typeMatcher = condition.typeMatcher
            if (typeMatcher is AnyTypeMatcher) {
                return mkOr(position.map { ConstantTrue })
            }

            if (typeMatcher is PrimitiveNameMatcher) {
                val types = primitiveTypes(method).filter { typeMatcher.matches(it.typeName) }
                return mkOr(types.flatMap { type -> position.map { TypeMatches(it, type) } })
            }

            typeMatcher as ClassMatcher

            val pkgMatcher = typeMatcher.pkg
            val clsMatcher = typeMatcher.classNameMatcher
            val cp = method.enclosingClass.classpath

            if (pkgMatcher is NameExactMatcher && clsMatcher is NameExactMatcher) {
                val type = cp.findTypeOrNull("${pkgMatcher.name}.${clsMatcher.name}") ?: return mkOr(emptyList())
                return mkOr(position.map { TypeMatches(it, type) })
            }

            // todo: reread, rethink, rewrite

            val allClasses = runBlocking {
                cp.hierarchyExt().findSubClasses(cp.objectClass, allHierarchy = true, includeOwn = true)
            }

            val types = allClasses.filter { pkgMatcher.matches(it.packageName) && clsMatcher.matches(it.simpleName) }
            return mkOr(types.flatMap { type -> position.map { TypeMatches(it, type.toType()) } }.toList())
        }

        override fun visit(condition: AnnotationType): Condition = TODO("Not yet implemented")

        override fun visit(condition: ConstantEq): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantLt): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantGt): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantMatches): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: SourceFunctionMatches): Condition {
            TODO("Not yet implemented")
        }

        override fun visit(condition: CallParameterContainsMark): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantTrue): Condition = condition

        override fun visit(condition: TypeMatches): Condition = error("Must not occur here")
    }

    companion object {
        fun fromPath(configPath: Path) = TaintConfigurationFeature(configPath.readText())
        fun fromJson(jsonConfig: String) = TaintConfigurationFeature(jsonConfig)
    }
}

fun JcClasspath.taintConfigurationFeature(): TaintConfigurationFeature = features
    ?.singleOrNull { it is TaintConfigurationFeature } as? TaintConfigurationFeature
    ?: error("No taint configuration feature found")
