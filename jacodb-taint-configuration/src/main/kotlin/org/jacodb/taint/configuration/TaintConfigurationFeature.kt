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

@file:Suppress("PublicApiImplicitType")

package org.jacodb.taint.configuration

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspathFeature
import org.jacodb.api.JcMethod
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.TypeName
import org.jacodb.api.ext.allSuperHierarchySequence
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectClass
import org.jacodb.api.ext.packageName
import org.jacodb.api.ext.short
import org.jacodb.api.ext.toType
import org.jacodb.impl.features.hierarchyExt
import java.nio.file.Path
import kotlin.io.path.readText

class TaintConfigurationFeature private constructor(
    jsonConfig: String,
    additionalSerializersModule: SerializersModule?,
) : JcClasspathFeature {
    private val rulesByClass: MutableMap<JcClassOrInterface, List<SerializedTaintConfigurationItem>> = hashMapOf()
    private val rulesForMethod: MutableMap<JcMethod, List<TaintConfigurationItem>> = hashMapOf()
    private val compiledRegex: MutableMap<String, Regex> = hashMapOf()

    private val configurationTrie: ConfigurationTrie by lazy {
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
            .map {
                when (it) {
                    is SerializedTaintEntryPointSource -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )

                    is SerializedTaintMethodSource -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )

                    is SerializedTaintMethodSink -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )

                    is SerializedTaintPassThrough -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )

                    is SerializedTaintCleaner -> it.copy(
                        condition = it.condition.accept(ConditionSimplifier())
                    )
                }
            }

        ConfigurationTrie(configuration, ::matches)
    }

    @Synchronized
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

        val rules = destination.distinct()
        rulesForMethod[method] = rules
        return rules
    }

    private fun getClassRules(clazz: JcClassOrInterface) = rulesByClass.getOrPut(clazz) {
        configurationTrie.getRulesForClass(clazz)
    }

    private fun FunctionMatcher.matches(method: JcMethod): Boolean {
        val functionNameMatcher = functionName
        val functionName = if (method.isConstructor) "init^" else method.name
        val functionNameMatches = matches(functionNameMatcher, functionName)
        if (!functionNameMatches) return false

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

    private fun Condition.resolve(method: JcMethod): Condition = accept(ConditionSpecializer(method))
    private fun List<Action>.resolve(method: JcMethod): List<Action> = flatMap { it.accept(ActionSpecializer(method)) }

    private fun specializePosition(method: JcMethod, position: Position): List<Position> {
        if (!inBounds(method, position)) return emptyList()
        if (position !is AnyArgument) return listOf(position)
        return method.parameters.indices.map { Argument(it) }.filter { inBounds(method, it) }
    }

    private fun mkOr(conditions: List<Condition>) = if (conditions.size == 1) conditions.single() else Or(conditions)
    private fun mkAnd(conditions: List<Condition>) = if (conditions.size == 1) conditions.single() else And(conditions)

    private fun inBounds(method: JcMethod, position: Position): Boolean =
        when (position) {
            AnyArgument -> method.parameters.isNotEmpty()
            is Argument -> position.index in method.parameters.indices
            This -> !method.isStatic
            Result -> method.returnType.typeName != PredefinedPrimitives.Void
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

        override fun visit(action: Action): List<Action> = error("Unexpected action $action")
    }

    private inner class ConditionSpecializer(val method: JcMethod) : ConditionVisitor<Condition> {
        override fun visit(condition: And): Condition =
            mkAnd(condition.args.map { it.accept(this) })

        override fun visit(condition: Or): Condition =
            mkOr(condition.args.map { it.accept(this) })

        override fun visit(condition: Not): Condition =
            Not(condition.arg.accept(this))

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
                val type = cp.findTypeOrNull("${pkgMatcher.name}$DOT_DELIMITER${clsMatcher.name}")
                    ?: return mkOr(emptyList())
                return mkOr(position.map { TypeMatches(it, type) })
            }

            val alternatives = typeMatcher.extractAlternatives()
            val disjuncts = mutableListOf<Condition>()

            alternatives.forEach { classMatcher ->
                val allClasses = runBlocking {
                    cp.hierarchyExt().findSubClasses(cp.objectClass, allHierarchy = true, includeOwn = true)
                }

                val types = allClasses.filter {
                    matches(classMatcher.pkg, it.packageName) && matches(classMatcher.classNameMatcher, it.simpleName)
                }

                disjuncts += types.flatMap { type -> position.map { TypeMatches(it, type.toType()) } }.toList()
            }

            return mkOr(disjuncts)
        }

        override fun visit(condition: AnnotationType): Condition = ConstantTrue // TODO("Not yet implemented")

        override fun visit(condition: ConstantEq): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantLt): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantGt): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantMatches): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: SourceFunctionMatches): Condition = ConstantTrue // TODO Not implemented yet

        override fun visit(condition: CallParameterContainsMark): Condition =
            mkOr(specializePosition(method, condition.position).map { condition.copy(position = it) })

        override fun visit(condition: ConstantTrue): Condition = condition

        override fun visit(condition: TypeMatches): Condition = error("Must not occur here")

        override fun visit(condition: Condition): Condition = condition
    }

    private inner class ConditionSimplifier : ConditionVisitor<Condition> {
        override fun visit(condition: And): Condition {
            val queue = ArrayDeque(condition.args)
            val args = mutableListOf<Condition>()
            while (queue.isNotEmpty()) {
                val it = queue.removeFirst().accept(this)
                if (it is And) {
                    queue += it.args
                } else {
                    args += it
                }
            }
            return mkAnd(args)
        }

        override fun visit(condition: Or): Condition {
            val queue = ArrayDeque(condition.args)
            val args = mutableListOf<Condition>()
            while (queue.isNotEmpty()) {
                val it = queue.removeLast().accept(this)
                if (it is Or) {
                    queue += it.args
                } else {
                    args += it
                }
            }
            return mkOr(args)
        }

        override fun visit(condition: Not): Condition {
            val arg = condition.arg.accept(this)
            return if (arg is Not) {
                // Eliminate double negation:
                arg.arg
            } else {
                Not(arg)
            }
        }

        override fun visit(condition: IsConstant): Condition = condition
        override fun visit(condition: IsType): Condition = condition
        override fun visit(condition: AnnotationType): Condition = condition
        override fun visit(condition: ConstantEq): Condition = condition
        override fun visit(condition: ConstantLt): Condition = condition
        override fun visit(condition: ConstantGt): Condition = condition
        override fun visit(condition: ConstantMatches): Condition = condition
        override fun visit(condition: SourceFunctionMatches): Condition = condition
        override fun visit(condition: CallParameterContainsMark): Condition = condition
        override fun visit(condition: ConstantTrue): Condition = condition
        override fun visit(condition: TypeMatches): Condition = condition

        override fun visit(condition: Condition): Condition = condition
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
