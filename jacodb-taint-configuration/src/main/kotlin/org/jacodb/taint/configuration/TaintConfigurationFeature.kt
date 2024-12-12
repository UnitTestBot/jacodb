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

package org.jacodb.taint.configuration

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.impl.cfg.util.isArray
import java.nio.file.Path
import kotlin.io.path.readText

class TaintConfigurationFeature private constructor(
    jsonConfig: String,
    additionalSerializersModule: SerializersModule?,
) : JcClasspathFeature {
    private val rulesByClass: MutableMap<JcClassOrInterface, List<SerializedTaintConfigurationItem>> = hashMapOf()
    private val rulesForMethod: MutableMap<JcMethod, List<TaintConfigurationItem>> = hashMapOf()
    private val compiledRegex: MutableMap<String, Regex> = hashMapOf()

    private val configurationTrie: JcConfigurationTrie by lazy {
        val serializers = SerializersModule {
            include(defaultSerializationModule)
            additionalSerializersModule?.let { include(it) }
        }

        val json = Json {
            classDiscriminator = CLASS_DISCRIMINATOR
            serializersModule = serializers
            prettyPrint = true
        }

        val configuration = json
            .decodeFromString<List<SerializedTaintConfigurationItem>>(jsonConfig)

        JcConfigurationTrie().apply { addRules(configuration) }
    }

    inner class JcConfigurationTrie : ConfigurationTrie<SerializedTaintConfigurationItem>() {
        override fun nameMatches(matcher: NameMatcher, name: String): Boolean = matches(matcher, name)

        override fun ruleClassNameMatcher(rule: SerializedTaintConfigurationItem): ClassMatcher =
            rule.methodInfo.cls

        override fun updateRuleClassNameMatcher(
            rule: SerializedTaintConfigurationItem,
            matcher: ClassMatcher
        ): SerializedTaintConfigurationItem {
            val updatedMethodInfo = rule.methodInfo.copy(cls = matcher)
            return rule.updateMethodInfo(updatedMethodInfo)
        }
    }

    @Synchronized
    fun getConfigForMethod(method: JcMethod): List<TaintConfigurationItem> =
        resolveConfigForMethod(method)

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

        val rules = destination.distinct()
        rulesForMethod[method] = rules
        return rules
    }

    private fun getClassRules(clazz: JcClassOrInterface) = rulesByClass.getOrPut(clazz) {
        configurationTrie.getRulesForClass(clazz)
    }

    private fun FunctionMatcher.matches(method: JcMethod): Boolean {
        val functionNameMatcher = functionName
        val functionNameMatches = if (method.isConstructor) {
            val constructorNames = arrayOf("init^", "<init>")
            constructorNames.any { matches(functionNameMatcher, it) }
        } else {
            matches(functionNameMatcher, method.name)
        }
        if (!functionNameMatches) return false

        if (parametersNumberMatcher != null && method.parameters.size != parametersNumberMatcher) return false

        val parameterMatches = parametersMatchers.all {
            val parameter = method.parameters.getOrNull(it.index) ?: return@all false
            it.typeMatcher.matches(parameter.type)
        }
        if (!parameterMatches) return false

        val returnTypeMatches = returnTypeMatcher.matches(method.returnType)
        if (!returnTypeMatches) return false

        // TODO add function's label processing

        require(modifier == -1) {
            "Unexpected modifier matcher value $modifier"
        }

        val isExcluded = exclude.any { it.matches(method) }
        return !isExcluded
    }

    private fun ClassMatcher.matches(fqn: String) = matches(
        fqn.substringBeforeLast(DOT_DELIMITER, missingDelimiterValue = ""),
        fqn.substringAfterLast(DOT_DELIMITER, missingDelimiterValue = fqn)
    )

    private fun ClassMatcher.matches(pkgName: String, className: String): Boolean {
        val packageMatches = matches(pkg, pkgName)
        if (!packageMatches) return false
        return matches(classNameMatcher, className)
    }

    private fun matches(nameMatcher: NameMatcher, nameToBeMatched: String): Boolean = when (nameMatcher) {
        AnyNameMatcher -> true
        is NameExactMatcher -> nameToBeMatched == nameMatcher.name
        is NamePatternMatcher -> {
            compiledRegex.getOrPut(nameMatcher.pattern) {
                nameMatcher.pattern.toRegex()
            }.matches(nameToBeMatched)
        }
    }

    private fun TypeMatcher.matches(typeName: TypeName): Boolean = matches(typeName.typeName)

    private fun TypeMatcher.matches(typeName: String): Boolean =
        when (this) {
            AnyTypeMatcher -> true
            is JcTypeNameMatcher -> this.typeName == typeName
            is ClassMatcher -> matches(typeName)
            is PrimitiveNameMatcher -> name == typeName
        }

    private fun SerializedTaintConfigurationItem.resolveForMethod(method: JcMethod): TaintConfigurationItem =
        when (this) {
            is SerializedTaintEntryPointSource -> TaintEntryPointSource(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintMethodSource -> TaintMethodSource(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintMethodSink -> TaintMethodSink(
                method,
                ruleNote,
                cwe,
                condition.resolve(method)
            )

            is SerializedTaintPassThrough -> TaintPassThrough(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )

            is SerializedTaintCleaner -> TaintCleaner(
                method,
                condition.resolve(method),
                actionsAfter.resolve(method)
            )
        }

    private fun SerializedCondition.resolve(method: JcMethod): Condition = this
        .specialize(method)
        .simplify()

    private fun List<SerializedAction>.resolve(method: JcMethod): List<Action> =
        flatMap { it.specialize(method) }

    private fun specializePosition(method: JcMethod, position: SerializedPosition): List<Position> = when (position) {
        is Position -> listOfNotNull(position.takeIf { inBounds(method, position) })

        AnyArgument -> if (method.parameters.isNotEmpty()) {
            method.parameters.indices.map { Argument(it) }.filter { inBounds(method, it) }
        } else {
            emptyList()
        }

        is SerializedPositionWithAccess -> specializePosition(method, position.base)
            .map { PositionWithAccess(it, position.access) }

        is AllAnnotatedArguments -> {
            method.parameters.indices.map { Argument(it) }
                .filter { inBounds(method, it) }
                .filter { methodAnnotationMatches(method, it, position.typeMatcher) }
        }
    }

    private fun inBounds(method: JcMethod, position: Position): Boolean =
        when (position) {
            is Argument -> position.index in method.parameters.indices
            This -> !method.isStatic
            Result -> method.returnType.typeName != PredefinedPrimitives.Void
            ResultAnyElement -> method.returnType.isArray
            is PositionWithAccess ->  error("")
        }

    private fun SerializedAction.specialize(method: JcMethod): List<Action> = when (this) {
        is SerializedAssignMark -> {
            specializePosition(method, position).map {
                AssignMark(mark, it)
            }
        }

        is SerializedCopyAllMarks -> {
            val from = specializePosition(method, from)
            val to = specializePosition(method, to)
            from.flatMap { fst ->
                to.mapNotNull { snd ->
                    if (fst == snd) return@mapNotNull null
                    CopyAllMarks(fst, snd)
                }
            }
        }

        is SerializedCopyMark -> {
            val from = specializePosition(method, from)
            val to = specializePosition(method, to)
            from.flatMap { fst ->
                to.mapNotNull { snd ->
                    if (fst == snd) return@mapNotNull null
                    CopyMark(mark, fst, snd)
                }
            }
        }

        is SerializedRemoveAllMarks -> {
            specializePosition(method, position)
                .map { RemoveAllMarks(it) }
        }

        is SerializedRemoveMark -> {
            specializePosition(method, position)
                .map { RemoveMark(mark, it) }
        }
    }

    private fun SerializedCondition.specialize(method: JcMethod): Condition = when (this) {
        is SerializedNot -> Not(arg.specialize(method))
        is SerializedOr -> mkOr(args.map { it.specialize(method) })
        is SerializedAnd -> mkAnd(args.map { it.specialize(method) })
        SerializedConstantTrue -> ConstantTrue

        is SerializedConstantEq -> mkOr(specializePosition(method, position).map { ConstantEq(it, value) })
        is SerializedConstantGt -> mkOr(specializePosition(method, position).map { ConstantGt(it, value) })
        is SerializedConstantLt -> mkOr(specializePosition(method, position).map { ConstantLt(it, value) })
        is SerializedIsConstant -> mkOr(specializePosition(method, position).map { IsConstant(it) })
        is SerializedConstantMatches -> mkOr(specializePosition(method, position)
            .map { ConstantMatches(it, pattern.toRegex()) })

        is SerializedContainsMark -> mkOr(specializePosition(method, position).map { ContainsMark(it, mark) })

        is SerializedAnnotationType -> {
            val positions = specializePosition(method, position)
            if (positions.any { methodAnnotationMatches(method, it, typeMatcher) }) {
                mkTrue()
            } else {
                mkFalse()
            }
        }

        is SerializedIsType -> {
            val position = specializePosition(method, position)
            val typeMatcherCondition = typeMatcher
                .resolveTypeMatcherCondition(method.enclosingClass.classpath, ::matches)

            mkOr(position.map(typeMatcherCondition))
        }

        is SerializedSourceFunctionMatches -> ConstantTrue // TODO Not implemented yet
    }

    private fun methodAnnotationMatches(method: JcMethod, position: Position, matcher: TypeMatcher): Boolean {
        when (position) {
            is Argument -> {
                val annotations = method.parameters.getOrNull(position.index)?.annotations
                return annotations?.any { matcher.matches(it.name) } ?: false
            }

            This -> {
                val annotations = method.annotations
                return annotations.any { matcher.matches(it.name) }
            }

            Result -> TODO("What does it mean?")
            is PositionWithAccess -> TODO("What does it mean?")
            ResultAnyElement -> error("Must not occur here")
        }
    }

    companion object {
        fun fromJson(
            jsonConfig: String,
            serializersModule: SerializersModule? = null,
        ) = TaintConfigurationFeature(jsonConfig, serializersModule)

        fun fromPath(
            configPath: Path,
            serializersModule: SerializersModule? = null,
        ) = fromJson(configPath.readText(), serializersModule)

        val defaultSerializationModule = SerializersModule {
            include(conditionModule)
            include(actionModule)
        }

        private const val CLASS_DISCRIMINATOR = "_"
    }
}
