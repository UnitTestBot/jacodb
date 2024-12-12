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

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.short
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

    private fun mkTrue(): Condition = ConstantTrue
    private fun mkFalse(): Condition = Not(ConstantTrue)

    private fun mkOr(conditions: List<Condition>) = when (conditions.size) {
        0 -> mkFalse()
        1 -> conditions.single()
        else -> Or(conditions)
    }

    private fun mkAnd(conditions: List<Condition>) = when (conditions.size) {
        0 -> mkTrue()
        1 -> conditions.single()
        else -> And(conditions)
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

    private fun SerializedCondition.specialize(method: JcMethod): Condition {
        return when (this) {
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

                if (typeMatcher is AnyTypeMatcher) {
                    return mkOr(position.map { ConstantTrue })
                }

                val cp = method.enclosingClass.classpath

                if (typeMatcher is JcTypeNameMatcher) {
                    val type = cp.findTypeOrNull(typeMatcher.typeName) ?: return ConstantTrue
                    return mkOr(position.map { TypeMatches(it, type) })
                }

                if (typeMatcher is PrimitiveNameMatcher) {
                    val types = primitiveTypes(method).filter { typeMatcher.matches(it.typeName) }
                    return mkOr(types.flatMap { type -> position.map { TypeMatches(it, type) } })
                }

                val typeMatchers = (typeMatcher as ClassMatcher).extractAlternatives()
                val unresolvedMatchers = mutableListOf<ClassMatcher>()
                val disjuncts = mutableListOf<Condition>()

                for (matcher in typeMatchers) {
                    val pkgMatcher = matcher.pkg
                    val clsMatcher = matcher.classNameMatcher

                    if (pkgMatcher !is NameExactMatcher || clsMatcher !is NameExactMatcher) {
                        unresolvedMatchers += matcher
                        continue
                    }

                    val type = cp.findTypeOrNull("${pkgMatcher.name}$DOT_DELIMITER${clsMatcher.name}")
                        ?: continue

                    position.mapTo(disjuncts) { TypeMatches(it, type) }
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
                            matches(classMatcher.pkg, packageName) && matches(classMatcher.classNameMatcher, simpleName)
                        }

                        matchedClassNames.flatMapTo(disjuncts) { (packageName, simpleName) ->
                            val type = cp.findTypeOrNull("${packageName}$DOT_DELIMITER${simpleName}")
                                ?: return@flatMapTo emptyList()

                            position.map { TypeMatches(it, type) }
                        }
                    }
                }

                return mkOr(disjuncts)
            }

            is SerializedSourceFunctionMatches -> ConstantTrue // TODO Not implemented yet
        }
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

    private val conditionSimplifier = object : ConditionVisitor<Condition> {
        override fun visit(condition: And): Condition {
            val queue = ArrayDeque(condition.args)
            val args = mutableListOf<Condition>()
            while (queue.isNotEmpty()) {
                val it = queue.removeFirst().accept(this)
                if (it is ConstantTrue) {
                    // skip
                } else if (it is Not && it.arg is ConstantTrue) {
                    return mkFalse()
                } else if (it is And) {
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
                val it = queue.removeFirst().accept(this)
                if (it is ConstantTrue) {
                    return mkTrue()
                } else if (it is Not && it.arg is ConstantTrue) {
                    // skip
                } else if (it is Or) {
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
        override fun visit(condition: ConstantEq): Condition = condition
        override fun visit(condition: ConstantLt): Condition = condition
        override fun visit(condition: ConstantGt): Condition = condition
        override fun visit(condition: ConstantMatches): Condition = condition
        override fun visit(condition: ContainsMark): Condition = condition
        override fun visit(condition: ConstantTrue): Condition = condition
        override fun visit(condition: TypeMatches): Condition = condition
    }

    private fun Condition.simplify(): Condition =
        accept(conditionSimplifier).toNnf(negated = false)


    private fun Condition.toNnf(negated: Boolean): Condition = when (this) {
        is Not -> arg.toNnf(!negated)

        is And -> if (!negated) {
            mkAndCondition(args) { it.toNnf(negated = false) }
        } else {
            mkOrCondition(args) { it.toNnf(negated = true) }
        }

        is Or -> if (!negated) {
            mkOrCondition(args) { it.toNnf(negated = false) }
        } else {
            mkAndCondition(args) { it.toNnf(negated = true) }
        }

        else -> if (negated) Not(this) else this
    }

    private inline fun mkOrCondition(
        args: List<Condition>,
        op: (Condition) -> Condition
    ): Or {
        val result = mutableListOf<Condition>()
        for (arg in args) {
            val mappedArg = op(arg)
            if (mappedArg is Or) {
                result.addAll(mappedArg.args)
            } else {
                result.add(mappedArg)
            }
        }
        return Or(result)
    }


    private inline fun mkAndCondition(
        args: List<Condition>,
        op: (Condition) -> Condition
    ): And {
        val result = mutableListOf<Condition>()
        for (arg in args) {
            val mappedArg = op(arg)
            if (mappedArg is And) {
                result.addAll(mappedArg.args)
            } else {
                result.add(mappedArg)
            }
        }
        return And(result)
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
