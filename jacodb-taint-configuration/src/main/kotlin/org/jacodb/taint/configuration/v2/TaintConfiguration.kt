package org.jacodb.taint.configuration.v2

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.decodeFromStream
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.taint.configuration.Action
import org.jacodb.taint.configuration.AnyNameMatcher
import org.jacodb.taint.configuration.AnyTypeMatcher
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.ClassMatcher
import org.jacodb.taint.configuration.Condition
import org.jacodb.taint.configuration.ConfigurationTrie
import org.jacodb.taint.configuration.ConstantEq
import org.jacodb.taint.configuration.ConstantGt
import org.jacodb.taint.configuration.ConstantLt
import org.jacodb.taint.configuration.ConstantMatches
import org.jacodb.taint.configuration.ConstantStringValue
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.CopyMark
import org.jacodb.taint.configuration.IsConstant
import org.jacodb.taint.configuration.JcTypeNameMatcher
import org.jacodb.taint.configuration.NameExactMatcher
import org.jacodb.taint.configuration.NameMatcher
import org.jacodb.taint.configuration.NamePatternMatcher
import org.jacodb.taint.configuration.Not
import org.jacodb.taint.configuration.Position
import org.jacodb.taint.configuration.PositionAccessor
import org.jacodb.taint.configuration.PositionWithAccess
import org.jacodb.taint.configuration.RemoveAllMarks
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.TaintCleaner
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintEntryPointSource
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.jacodb.taint.configuration.This
import org.jacodb.taint.configuration.TypeMatcher
import org.jacodb.taint.configuration.mkAnd
import org.jacodb.taint.configuration.mkFalse
import org.jacodb.taint.configuration.mkOr
import org.jacodb.taint.configuration.mkTrue
import org.jacodb.taint.configuration.resolveTypeMatcherCondition
import org.jacodb.taint.configuration.simplify
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream

class TaintConfiguration {
    private val yaml = Yaml(configuration = YamlConfiguration(codePointLimit = Int.MAX_VALUE))

    fun loadConfig(path: Path) = loadConfig(path.inputStream())

    fun loadConfig(stream: InputStream) = loadConfig(yaml.decodeFromStream<SerializedTaintConfig>(stream))

    private val entryPointConfig = TaintRulesStorage<SerializedRule.EntryPoint, TaintEntryPointSource>()
    private val sourceConfig = TaintRulesStorage<SerializedRule.Source, TaintMethodSource>()
    private val sinkConfig = TaintRulesStorage<SerializedRule.Sink, TaintMethodSink>()
    private val passThroughConfig = TaintRulesStorage<SerializedRule.PassThrough, TaintPassThrough>()
    private val cleanerConfig = TaintRulesStorage<SerializedRule.Cleaner, TaintCleaner>()

    private val taintMarks = hashMapOf<String, TaintMark>()

    fun loadConfig(config: SerializedTaintConfig) {
        entryPointConfig.addRules(config.entryPoint)
        sourceConfig.addRules(config.source)
        sinkConfig.addRules(config.sink)
        passThroughConfig.addRules(config.passThrough)
        cleanerConfig.addRules(config.cleaner)
    }

    fun entryPointForMethod(method: JcMethod): List<TaintEntryPointSource> = entryPointConfig.getConfigForMethod(method)
    fun sourceForMethod(method: JcMethod): List<TaintMethodSource> = sourceConfig.getConfigForMethod(method)
    fun sinkForMethod(method: JcMethod): List<TaintMethodSink> = sinkConfig.getConfigForMethod(method)
    fun passThroughForMethod(method: JcMethod): List<TaintPassThrough> = passThroughConfig.getConfigForMethod(method)
    fun cleanerForMethod(method: JcMethod): List<TaintCleaner> = cleanerConfig.getConfigForMethod(method)

    private inner class TaintRulesStorage<S : SerializedRule, T : TaintConfigurationItem> {
        private val rulesTrie = TaintConfigurationTrie<S>()
        private val classRules = hashMapOf<JcClassOrInterface, List<S>>()
        private val methodItems = hashMapOf<JcMethod, List<T>>()

        fun addRules(rules: List<S>) {
            rulesTrie.addRules(rules)

            // invalidate rules cache
            classRules.clear()
            methodItems.clear()
        }

        @Synchronized
        fun getConfigForMethod(method: JcMethod): List<T> = methodItems.getOrPut(method) {
            resolveMethodItems(method)
        }

        private fun getClassRules(clazz: JcClassOrInterface) = classRules.getOrPut(clazz) {
            rulesTrie.getRulesForClass(clazz)
        }

        private fun resolveMethodItems(method: JcMethod): List<T> {
            val rules = getClassRules(method.enclosingClass).toMutableList()
            method.enclosingClass.allSuperHierarchySequence.distinct().forEach { cls ->
                getClassRules(cls).filterTo(rules) { it.overrides }
            }

            rules.removeAll { !it.function.name.matchFunctionName(method) }
            rules.removeAll { it.signature?.matchFunctionSignature(method) == false }

            return rules.map { resolveMethodRule(it, method) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun resolveMethodRule(rule: S, method: JcMethod): T =
            rule.resolveMethodRule(method) as T
    }

    private inner class TaintConfigurationTrie<T : SerializedRule> : ConfigurationTrie<T>() {
        override fun nameMatches(matcher: NameMatcher, name: String): Boolean =
            matcher.serializedNameMatcher().match(name)

        override fun ruleClassNameMatcher(rule: T): ClassMatcher {
            val function = rule.function
            return ClassMatcher(function.`package`.nameMatcher(), function.`class`.nameMatcher())
        }

        override fun updateRuleClassNameMatcher(rule: T, matcher: ClassMatcher): T {
            val updatedFunction = SerializedFunctionNameMatcher.Complex(
                `package` = matcher.pkg.serializedNameMatcher(),
                `class` = matcher.classNameMatcher.serializedNameMatcher(),
                name = rule.function.name,
            ).simplify()

            @Suppress("UNCHECKED_CAST")
            return when (val r = rule as SerializedRule) {
                is SerializedRule.Cleaner -> r.copy(function = updatedFunction)
                is SerializedRule.EntryPoint -> r.copy(function = updatedFunction)
                is SerializedRule.PassThrough -> r.copy(function = updatedFunction)
                is SerializedRule.Sink -> r.copy(function = updatedFunction)
                is SerializedRule.Source -> r.copy(function = updatedFunction)
            } as T
        }
    }

    private fun SerializedNameMatcher.nameMatcher(): NameMatcher = when (this) {
        is SerializedNameMatcher.Simple -> if (value == "*") AnyNameMatcher else NameExactMatcher(value)
        is SerializedNameMatcher.Pattern -> NamePatternMatcher(pattern)
        is SerializedNameMatcher.ClassPattern -> error("Unexpected serialized name: $this")
    }

    private fun SerializedNameMatcher.typeNameMatcher(): TypeMatcher = when (this) {
        is SerializedNameMatcher.Simple -> if (value == "*") AnyTypeMatcher else JcTypeNameMatcher(value)

        is SerializedNameMatcher.Pattern -> if (pattern == ".*") {
            AnyTypeMatcher
        } else {
            TODO()
        }

        is SerializedNameMatcher.ClassPattern -> {
            ClassMatcher(`package`.nameMatcher(), `class`.nameMatcher())
        }
    }

    private fun NameMatcher.serializedNameMatcher(): SerializedNameMatcher = when (this) {
        is NameExactMatcher -> SerializedNameMatcher.Simple(name)
        is NamePatternMatcher -> SerializedNameMatcher.Pattern(pattern)
        AnyNameMatcher -> SerializedNameMatcher.Pattern(".*")
    }

    private val compiledMatchers = hashMapOf<SerializedNameMatcher.Pattern, Regex>()

    private fun SerializedNameMatcher.match(name: String): Boolean = when (this) {
        is SerializedNameMatcher.Simple -> if (value == "*") true else value == name
        is SerializedNameMatcher.Pattern -> compiledMatchers.getOrPut(this) { pattern.toRegex() }.matches(name)
        is SerializedNameMatcher.ClassPattern -> {
            `package`.match(name.substringBeforeLast('.', missingDelimiterValue = ""))
                    && `class`.match(name.substringAfterLast('.', missingDelimiterValue = name))
        }
    }

    private fun SerializedNameMatcher.matchFunctionName(method: JcMethod): Boolean {
        if (match(method.name)) return true

        if (method.isConstructor) {
            val constructorNames = arrayOf("init^", "<init>")
            if (constructorNames.any { match(it) }) return true
        }

        return false
    }

    private fun SerializedSignatureMatcher.matchFunctionSignature(method: JcMethod): Boolean {
        when (this) {
            is SerializedSignatureMatcher.Simple -> {
                if (method.parameters.size != args.size) return false

                if (!`return`.match(method.returnType.typeName)) return false

                return args.zip(method.parameters).all { (matcher, param) ->
                    matcher.match(param.type.typeName)
                }
            }

            is SerializedSignatureMatcher.Partial -> {
                if (`return` != null && !`return`.match(method.returnType.typeName)) return false

                if (params != null) {
                    for (param in params) {
                        val methodParam = method.parameters.getOrNull(param.index) ?: return false
                        if (!param.type.match(methodParam.type.typeName)) return false
                    }
                }

                return true
            }
        }
    }

    private fun SerializedRule.resolveMethodRule(method: JcMethod): TaintConfigurationItem = when (this) {
        is SerializedRule.EntryPoint -> {
            TaintEntryPointSource(method, ConstantTrue, taint.flatMap { it.resolve(method) })
        }

        is SerializedRule.Source -> {
            TaintMethodSource(method, condition.resolve(method).simplify(), taint.flatMap { it.resolve(method) })
        }

        is SerializedRule.Sink -> {
            TaintMethodSink(method, note, cwe, condition.resolve(method).simplify())
        }

        is SerializedRule.PassThrough -> {
            TaintPassThrough(method, condition.resolve(method).simplify(), copy.flatMap { it.resolve(method) })
        }

        is SerializedRule.Cleaner -> {
            TaintCleaner(method, condition.resolve(method).simplify(), cleans.flatMap { it.resolve(method) })
        }
    }

    private fun taintMark(name: String): TaintMark = taintMarks.getOrPut(name) { TaintMark(name) }

    private fun SerializedCondition?.resolve(method: JcMethod): Condition = when (this) {
        null -> ConstantTrue
        is SerializedCondition.Not -> Not(not.resolve(method))
        is SerializedCondition.And -> mkAnd(allOf.map { it.resolve(method) })
        is SerializedCondition.Or -> mkOr(anyOf.map { it.resolve(method) })
        SerializedCondition.True -> ConstantTrue
        is SerializedCondition.AnnotationType -> {
            val containsAnnotation = pos.resolve(method, annotatedWith).any()
            if (containsAnnotation) mkTrue() else mkFalse()
        }

        is SerializedCondition.ConstantEq -> mkOr(
            pos.resolve(method).map { ConstantEq(it, ConstantStringValue(constantEq)) })

        is SerializedCondition.ConstantGt -> mkOr(
            pos.resolve(method).map { ConstantGt(it, ConstantStringValue(constantGt)) })

        is SerializedCondition.ConstantLt -> mkOr(
            pos.resolve(method).map { ConstantLt(it, ConstantStringValue(constantLt)) })

        is SerializedCondition.ConstantMatches -> mkOr(
            pos.resolve(method).map { ConstantMatches(it, constantMatches.toRegex()) })

        is SerializedCondition.IsConstant -> mkOr(isConstant.resolve(method).map { IsConstant(it) })
        is SerializedCondition.ContainsMark -> mkOr(
            pos.resolvePosition(method).map { ContainsMark(it, taintMark(tainted)) })

        is SerializedCondition.IsType -> {
            val typeMatcher = typeIs.typeNameMatcher()
            val types = typeMatcher.resolveTypeMatcherCondition(method.enclosingClass.classpath) { matcher, name ->
                matcher.serializedNameMatcher().match(name)
            }
            mkOr(pos.resolve(method).map(types))
        }
    }

    private fun SerializedTaintAssignAction.resolve(method: JcMethod): List<AssignMark> =
        pos.resolvePosition(method, annotatedWith).map { AssignMark(taintMark(kind), it) }

    private fun SerializedTaintPassAction.resolve(method: JcMethod): List<Action> =
        from.resolvePosition(method).flatMap { fromPos ->
            to.resolvePosition(method).map { toPos ->
                if (taintKind == null) {
                    CopyAllMarks(fromPos, toPos)
                } else {
                    CopyMark(taintMark(taintKind), fromPos, toPos)
                }
            }
        }

    private fun SerializedTaintCleanAction.resolve(method: JcMethod): List<Action> =
        pos.resolvePosition(method).map { pos ->
            if (taintKind == null) {
                RemoveAllMarks(pos)
            } else {
                RemoveMark(taintMark(taintKind), pos)
            }
        }

    private fun PositionBaseWithModifiers.resolvePosition(
        method: JcMethod,
        annotation: SerializedNameMatcher? = null
    ): List<Position> {
        val resolvedBase = base.resolve(method, annotation)
        return when (this) {
            is PositionBaseWithModifiers.BaseOnly -> resolvedBase
            is PositionBaseWithModifiers.WithModifiers -> {
                resolvedBase.map { b ->
                    modifiers.fold(b) { basePos, modifier ->
                        val accessor = when (modifier) {
                            PositionModifier.AllFields -> PositionAccessor.AllPositions
                            PositionModifier.ArrayElement -> PositionAccessor.ElementAccessor
                            is PositionModifier.Field -> {
                                PositionAccessor.FieldAccessor(
                                    modifier.className,
                                    modifier.fieldName,
                                    modifier.fieldType
                                )
                            }
                        }
                        PositionWithAccess(basePos, accessor)
                    }
                }
            }
        }
    }

    private fun PositionBase.resolve(method: JcMethod, annotation: SerializedNameMatcher? = null): List<Position> {
        when (this) {
            is PositionBase.Argument -> {
                if (idx != null) {
                    val param = method.parameters.getOrNull(idx) ?: return emptyList()
                    if (annotation != null && !param.annotations.matched(annotation)) return emptyList()

                    return listOf(Argument(idx))
                } else {
                    return method.parameters.filter { param ->
                        annotation == null || param.annotations.matched(annotation)
                    }.map { Argument(it.index) }
                }
            }

            PositionBase.Result -> {
                if (method.returnType.typeName == PredefinedPrimitives.Void) return emptyList()
                if (annotation != null) {
                    TODO()
                }

                return listOf(Result)
            }

            PositionBase.This -> {
                if (method.isStatic) return emptyList()
                if (annotation != null && !method.annotations.matched(annotation)) return emptyList()

                return listOf(This)
            }
        }
    }

    private fun List<JcAnnotation>.matched(matcher: SerializedNameMatcher): Boolean = any { matcher.match(it.name) }
}
