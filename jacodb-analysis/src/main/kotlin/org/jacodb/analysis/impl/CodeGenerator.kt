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

package org.jacodb.analysis.impl

import com.sun.org.apache.xpath.internal.operations.Bool
import net.lingala.zip4j.ZipFile
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.Collections.min
import kotlin.io.path.*
import kotlin.random.Random

// region generation

class DeZipper {
    private fun transferTemplateZipToTemp(): Path {
        val pathWhereToUnzipTemplate = Files.createTempFile(null, null)
        this.javaClass.classLoader.getResourceAsStream("UtBotTemplateForIfdsSyntheticTests.zip")!!
            .use { templateFromResourceStream ->
                FileOutputStream(pathWhereToUnzipTemplate.toFile()).use { streamToLocationForTemplate ->
                    templateFromResourceStream.copyTo(streamToLocationForTemplate)
                }
            }
        return pathWhereToUnzipTemplate
    }

    fun dezip(pathToDirectoryWhereToUnzipTemplate: Path) {
        pathToDirectoryWhereToUnzipTemplate.createDirectories()
        val templateZipAtTemp = transferTemplateZipToTemp()
        ZipFile(templateZipAtTemp.toFile()).extractAll(pathToDirectoryWhereToUnzipTemplate.absolutePathString())
    }
}

class AccessibilityCache(private val graph: Map<Int, Set<Int>>) {
    private val used = Array(graph.size) { 0 }
    private var currentWave = 0
    val badQuery = -1 to -1
    val badPath = listOf(-1)
    private var lastQuery = badQuery
    private var lastQueryPath = mutableListOf<Int>()

    private fun dfs(u: Int, target: Int): Boolean {
        used[u] = currentWave
        if (u == target) {
            lastQueryPath = mutableListOf(u)
            return true
        }

        for (v in graph.getValue(u)) {
            if (used[v] != currentWave && dfs(v, target)) {
                lastQueryPath.add(u)
                return true
            }
        }

        return false
    }

    fun isAccessible(u: Int, v: Int): Boolean {
        ++currentWave
        lastQuery = badQuery
        if (dfs(u, v)) {
            lastQuery = u to v
            return true
        }
        return false
    }


    fun getAccessPath(u: Int, v: Int): List<Int> {
        if (lastQuery == u to v) return lastQueryPath

        if (isAccessible(u, v)) return lastQueryPath

        return badPath
    }
}

// endregion

// region common

// primary languages - java, cpp.
// secondary - python, go, js, kotlin, etc.

// language features that are not supported and (most probably) will never be
// 1. package-private/internal/sealed modifiers - as they are not present in all languages
// 2. cpp private inheritance, multiple inheritance - as this complicates code model way too much
// 3. cpp constructor initialization features - complicates ast too much
// 4.

/**
 * Anything that can be dumped via [TargetLanguage]
 */
interface CodeElement {
    // TODO some way to dump
}

/**
 * Universal visibility that is present in most of the languages.
 */
enum class VisibilityModifier {
    PUBLIC,
    PRIVATE,
    // TODO PROTECTED - as it is relevant only when we will have inheritance
}

/**
 * Both tag for inheritance and type form
 */
enum class InheritanceModifier {
    ABSTRACT,
    OPEN,
    FINAL,
    INTERFACE,
    STATIC,
    ENUM
}

/**
 * Entity that can be located
 */
interface NameOwner : CodeElement {
    val shortName: String
    val fqnName: String
        get() = shortName
}

interface VisibilityOwner : CodeElement {
    val visibility: VisibilityModifier
}

interface Inheritable : CodeElement {
    val inheritanceModifier: InheritanceModifier
    val inheritedFrom: Inheritable?
}

/**
 * Anything that resides in type
 */
interface TypePart : CodeElement {
    val containingType: TypeRepresentation
}

/**
 * Anything that resides in tyoe and can be located
 */
interface NamedTypePart : NameOwner, TypePart {
    override val fqnName: String
        get() = containingType.fqnName + "." + shortName
}
// endregion

// region type

// we will differ static class and instance class like in kotlin.
// for Java and CPP this means that in case of static elements we will create static duplicate
/**
 * Class, interface, abstract class, structure, enum.
 */
interface TypeRepresentation : VisibilityOwner, NameOwner, Inheritable {
    // most of the time this is ObjectCreationExpression - for open and final classes
    // in case of abstract, static, interface and enum - this would throw exception
    val defaultValue: RValueRepresentation
    // if created class is not abstract - we will find all methods that do not have implementation
    // and provide default overload for it. Default means completely empty body, same parameters and return type.
    val typeParts: Collection<TypePart>
    // there are no way to add interface as we expect:
    // 1. all interface hierarchy will be predefined beforehand - in graph
    // 2. all class hierarchy also will be predefined beforehand - as tree
    // 3. we will know about abstract class to interface mappings
    // 4. we will topologically sort that class-interface graph and generate hierarchy accordingly
    val implementedInterfaces: Collection<TypeRepresentation>
    // as we know class-interface hierarchy - we do not need to redefine who we inherited from
    override val inheritedFrom: TypeRepresentation?

    val defaultConstructor: ConstructorRepresentation
    val staticCounterPart: TypeRepresentation
    val instanceType: InstanceType

    fun overrideMethod(methodToOverride: MethodRepresentation): MethodRepresentation
    fun createMethod(
        name: String,
        parameters: List<LValueRepresentation>,
        returnType: TypeRepresentation
    ): MethodRepresentation

    fun createConstructor(parameter: List<LValueRepresentation>, parentConstructorCall: ObjectCreationExpression?): ConstructorRepresentation
    fun createField(name: String, type: TypeRepresentation, initialValue: RValueRepresentation): FieldRepresentation

    val implementedMethods: Collection<MethodRepresentation>
        get() = typeParts.filterIsInstance<MethodRepresentation>()
    val allAvailableMethods: Collection<MethodRepresentation>
        get() = (implementedInterfaces.flatMap { it.allAvailableMethods })
            .union(inheritedFrom?.allAvailableMethods ?: mutableListOf())
            .union(implementedMethods)

    val constructors: Collection<ConstructorRepresentation>
        get() = typeParts.filterIsInstance<ConstructorRepresentation>()

    val implementedFields: Collection<FieldRepresentation>
        get() = typeParts.filterIsInstance<FieldRepresentation>()
    val allAvailableFields: Collection<FieldRepresentation>
        get() = implementedFields.union(inheritedFrom?.allAvailableFields ?: emptyList())

    fun getImplementedField(name: String): FieldRepresentation? = implementedFields.singleOrNull { it.shortName == name }
    fun getField(name: String): FieldRepresentation? = allAvailableFields.singleOrNull { it.shortName == name }
    fun getImplementedFields(type: ResolvedType): Collection<FieldRepresentation> = implementedFields.filter { it.type == type }
    fun getFields(type: ResolvedType): Collection<FieldRepresentation> = allAvailableFields.filter { it.type == type }

    fun getImplementedMethods(name: String): Collection<MethodRepresentation> = implementedMethods.filter { it.shortName == name }
    fun getMethods(name: String): Collection<MethodRepresentation> = allAvailableMethods.filter { it.shortName == name }
}

//endregion

// region functions

/**
 * Anything that can be called. Parent for functions, methods, lambdas, constructors, destructors etc.
 */
interface CallableRepresentation : CodeElement {
    val signature: String
        get() = parameters.joinToString { it.type.fqnName }

    // consists from parameters and local variables
    val visibleLocals: Collection<CallableLocal>
    val returnType: ResolvedType

    // should be aware of local variables
    fun createParameter(name: String, type: ResolvedType): ParameterRepresentation

    // should be aware of parameters
    fun createLocalVariable(
        name: String,
        type: ResolvedType,
        initialValue: RValueRepresentation
    ): LocalVariableRepresentation

    /**
     * Each site represent different way to execute this callable
     */
    val sites: Collection<SiteRepresentation>
    fun createCallSite(invokedOn: RValueRepresentation?, callee: CallableRepresentation): CallSiteRepresentation
    val terminationSite: TerminationSiteRepresentation

    val graphId: Int

    val parameters: Collection<ParameterRepresentation>
        get() = visibleLocals.filterIsInstance<ParameterRepresentation>()
    val localVariables: Collection<LocalVariableRepresentation>
        get() = visibleLocals.filterIsInstance<LocalVariableRepresentation>()

    fun getLocal(name: String) = visibleLocals.singleOrNull { it.shortName == name }
    fun getLocals(type: ResolvedType) = visibleLocals.filter { it.type == type }

    fun getLocalVariable(name: String) = localVariables.singleOrNull { it.shortName == name }
    fun getLocalVariables(type: ResolvedType) = localVariables.filter { it.type == type }
    fun getOrCreateLocalVariable(
        name: String,
        type: ResolvedType,
        initialValue: RValueRepresentation
    ) = getLocalVariable(name) ?: createLocalVariable(name, type, initialValue)

    fun getParameter(name: String) = parameters.singleOrNull { it.shortName == name }
    fun getParameters(type: ResolvedType) = parameters.filter { it.type == type }
    fun getOrCreateParameter(name: String, type: ResolvedType) =
        getParameter(name) ?: createParameter(name, type)
}

interface ConstructorRepresentation : CallableRepresentation, VisibilityOwner, TypePart {
    val parentConstructorCall: ObjectCreationExpression?
    override val returnType: ResolvedType
        get() = containingType.instanceType
}

/**
 * Any named functions. Global, static functions and methods.
 */
interface FunctionRepresentation : CallableRepresentation, VisibilityOwner, NameOwner {
    override val fqnName: String
        get() = shortName
}

interface MethodRepresentation : FunctionRepresentation, NamedTypePart, Inheritable {
    val overriddenMethod: MethodRepresentation?
    override val fqnName: String
        get() = super<NamedTypePart>.fqnName
}

// endregion

// region sites

/**
 * Some code block in function. Any callable instance is list of sites.
 */
interface SiteRepresentation : CodeElement {
    val siteId: Int
    val parentCallable: CallableRepresentation
}

/**
 * Site represents call and all prepartion for this call
 */
interface CallSiteRepresentation : SiteRepresentation {
    val invocationExpression: InvocationExpression
}

/**
 * End of any call sequence.
 */
interface TerminationSiteRepresentation : SiteRepresentation {
    fun addDereference(reference: RValueRepresentation)
}

// endregion

// region code expressions
/**
 * Expression that have arguments. Each argument should specify for which parameter it is used for.
 * If some parameters are not matched - default values of types will be used.
 */
interface ArgumentsOwnerExpression : RValueRepresentation {
    val parameterToArgument: Map<ParameterRepresentation, RValueRepresentation>
    fun addInCall(parameter: ParameterRepresentation, argument: RValueRepresentation)
}

interface InvocationExpression : ArgumentsOwnerExpression {
    val invokedCallable: CallableRepresentation
    val invokedOn: RValueRepresentation?

    override val type: ResolvedType
        get() = invokedCallable.returnType
}

interface FunctionInvocationExpression : InvocationExpression {
    override val invokedOn: RValueRepresentation?
        get() = null
    override val invokedCallable: FunctionRepresentation
}

interface MethodInvocationExpression : InvocationExpression {
    val invokedMethod: MethodRepresentation
    override val invokedOn: RValueRepresentation

    override val invokedCallable: MethodRepresentation
        get() = invokedMethod
}

interface ObjectCreationExpression : InvocationExpression {
    val invokedConstructor: ConstructorRepresentation

    override val invokedCallable: ConstructorRepresentation
        get() = invokedConstructor
    override val invokedOn: RValueRepresentation?
        get() = null
}

/**
 * Hack if AST is not sufficient, proposing [TargetLanguage] writing [substitution] string directly.
 * Create anonymous object of this interface and mock anything you need to.
 * [TargetLanguage] will use [substitution] for generating code.
 */
interface DirectStringSubstitution : CodeElement {
    val substitution: String
}
// endregion

// region resolvedType

enum class TypeTag(private val number: Int) {
    EMPTY(0),
    ARRAY(1 shl 0),
    NULLABLE(1 shl 1);
}

interface ResolvedType : CodeElement, NameOwner {
    val tag: EnumSet<TypeTag>
    fun furthestElementType(): InstanceType
    fun wrapInArray(): ArrayType
    fun nullable(): ResolvedType
}

interface ArrayType: ResolvedType {
    val element: ResolvedType
}

interface InstanceType: ResolvedType {
    val representation: TypeRepresentation
}

// endregion

// region values
/**
 * Anything that can be assigned or invoked.
 */
interface RValueRepresentation : CodeElement {
    val type: ResolvedType
}

/**
 * Anything you can assign value to.
 */
interface LValueRepresentation : NameOwner, RValueRepresentation

/**
 * Named entities of callable. For now we require all locals to be unique.
 */
interface CallableLocal : LValueRepresentation {
    val parentCallable: CallableRepresentation
}

interface ParameterRepresentation : CallableLocal {
    val indexInSignature: Int
}

interface InitializerOwner {
    val initialValue: RValueRepresentation
}

interface FieldRepresentation : TypePart, LValueRepresentation, InitializerOwner

interface LocalVariableRepresentation : CallableLocal, InitializerOwner
// endregion

// region typeImpl

open class TypeImpl(
    final override val shortName: String,
    final override val visibility: VisibilityModifier,
    final override val inheritanceModifier: InheritanceModifier,
    final override val inheritedFrom: TypeRepresentation? = null,
    interfaces: List<TypeRepresentation> = emptyList(),
    defaultConstructorParameters: List<LValueRepresentation> = emptyList(),
    parentConstructorCall: ObjectCreationExpression? = null
) : TypeRepresentation {
    final override val implementedInterfaces = interfaces.toSet()

    init {
        // interfaces have separate inheritance mechanism
        // enum and static classes cant inherited classes
        if (inheritanceModifier == InheritanceModifier.INTERFACE ||
            inheritanceModifier == InheritanceModifier.ENUM ||
            inheritanceModifier == InheritanceModifier.STATIC) {
            assert(inheritedFrom == null)
        }

        if (inheritedFrom != null) {
            // type can be inherited only from abstract or open
            assert(inheritedFrom.inheritanceModifier == InheritanceModifier.ABSTRACT ||
                    inheritedFrom.inheritanceModifier == InheritanceModifier.OPEN)
            // if type is inherited - it can only be abstract, open or final
            assert(inheritanceModifier == InheritanceModifier.ABSTRACT ||
                    inheritanceModifier == InheritanceModifier.OPEN ||
                    inheritanceModifier == InheritanceModifier.FINAL)
        }
    }

    override val instanceType: InstanceType
        get() = TODO("Not yet implemented")
    override val staticCounterPart: TypeRepresentation by lazy { StaticCounterPartTypeImpl(this) }
    override val defaultConstructor: ConstructorRepresentation by lazy { createConstructor(defaultConstructorParameters, parentConstructorCall) }
    override val defaultValue: ObjectCreationExpression by lazy { ObjectCreationExpressionImpl(defaultConstructor) }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TypeImpl)
            return false

        // different types should have different fqn names
        assert(this === other || this.fqnName != other.fqnName)

        return other === this
    }

    override fun hashCode(): Int {
        // type is uniquely identified by its fqn name
        return fqnName.hashCode()
    }

    final override val typeParts = mutableListOf<TypePart>()

    // this is the hardest method as it should check
    // 1. method really exists and relates to this type hierarchy
    // 2. not already overridden
    // 3. no collision with already defined methods
    // 4. signature compatability
    // 5. name compatability
    // 6. return type compatability
    // 7. available for override
    // 8. no final override in between 2 types
    /**
     * @param methodToOverride method available for override. Should be defined in type parents
     * @throws MethodAlreadyOverridenException if [methodToOverride] is already overriden in this class
     * @throws MethodNotFoundException if [methodToOverride] is not defined in any of parent types
     * @throws MethodCollisionException if [methodToOverride] collides with some other method already defined method in this type hierarchy
     */
    override fun overrideMethod(methodToOverride: MethodRepresentation): MethodRepresentation {
        // 1. check not overridden
        // 2. check no same
        // 3. check no any other conflict
        TODO("Not yet implemented")
    }

    // throws if fqn + parameters already presented in available methods
    /**
     * Creates method from scratch.
     * @throws MethodCollisionException proposed method is already declared in this type hierarchy
     */
    override fun createMethod(
        name: String,
        parameters: List<LValueRepresentation>,
        returnType: TypeRepresentation
    ): MethodRepresentation {

        return MethodImpl(this, null)
    }

    // throws if signature already present
    // or
    // parent call is incorrect - not provided or incorrect parent constructor targeted
    /**
     * Creates constructor for this type
     * @throws ConstructorCollisionException proposed constructor is already declared in this type
     * @throws NoParentCallException this type requires parent initialization, provide which parent constructor to call
     */
    override fun createConstructor(parameter: List<LValueRepresentation>, parentConstructorCall: ObjectCreationExpression?): ConstructorRepresentation {
        // 1. provide default constructor parameters
        // 2. create default constructor
        // 3. assert constructor is created with parent call in case of need
        // 4. then we can take object creation expression with this constructor

    }

    // throws if name already present in hierachy
    /**
     * Creates field in this type
     * @throws FieldCollisionException proposed field is already declared in this type hierarchy
     */
    override fun createField(
        name: String,
        type: TypeRepresentation,
        initialValue: RValueRepresentation
    ): FieldRepresentation {
        TODO("Not yet implemented")
    }
}

class StaticCounterPartTypeImpl(typeImpl: TypeImpl): TypeImpl(), TypeRepresentation {
    override val staticCounterPart: TypeRepresentation = typeImpl

    override fun overrideMethod(methodToOverride: MethodRepresentation): MethodRepresentation {
        throw IllegalStateException("Methods in static counterparts cannot be overridden")
    }

    override fun createConstructor(
        parameter: List<LValueRepresentation>,
        parentConstructorCall: ObjectCreationExpression?
    ): ConstructorRepresentation {
        throw IllegalStateException("Constructors in static counterparts cannot be created")
    }

    override val defaultValue
        // todo class<type>?
        get() = throw IllegalStateException("static types dont have default value")

    override val defaultConstructor: ConstructorRepresentation
        // todo static initializer?
        get() = throw IllegalStateException("static types cannot be instantiated")

    override val instanceType: InstanceType
        // todo Class<Type>
        get() = throw IllegalStateException("static types cannot be referenced")
}

// endregion

// region sites_impl

abstract class SiteImpl : SiteRepresentation {
    override fun equals(other: Any?): Boolean {
        if (other !is SiteRepresentation) {
            return false
        }

        if (parentCallable == other.parentCallable && siteId == other.siteId) {
            assert(other === this)
        }

        return other === this
    }

    override fun hashCode(): Int {
        return parentCallable.hashCode() * 31 + siteId
    }
}

class CallSiteImpl(
    // unique identifier with the function
    override val siteId: Int,
    override val parentCallable: CallableRepresentation,
    override val invocationExpression: InvocationExpression
) : SiteImpl(), CallSiteRepresentation

class TerminationSiteImpl(override val parentCallable: CallableRepresentation) : SiteImpl(),
    TerminationSiteRepresentation {
    private val dereferences = mutableListOf<RValueRepresentation>()

    override val siteId: Int = Int.MAX_VALUE
    override fun addDereference(reference: RValueRepresentation) {
        // you can add multiple dereferences on the same reference
        dereferences.add(reference)
    }
}

// endregion

// region callables_impl

open class CallableImpl(
    override val graphId: Int,
    override val returnType: ResolvedType,
    parameters: List<LValueRepresentation> = emptyList()
) : CallableRepresentation {
    override fun hashCode(): Int {
        return graphId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CallableImpl)
            return false

        if (graphId == other.graphId) {
            // we uniquely identify callable instance by theirs vertices in graph
            assert(this === other)
        }

        return this === other
    }

    override val visibleLocals = parameters
        .mapIndexed { index, it -> ParameterImpl(it.type, it.shortName, this, index) }
        .toMutableList<CallableLocal>()
    override val sites = mutableListOf<SiteRepresentation>()

    override fun createLocalVariable(
        name: String,
        type: ResolvedType,
        initialValue: RValueRepresentation
    ): LocalVariableRepresentation {
        assert(getLocal(name) == null) { "Already have local entity with name $name" }
        return LocalVariableImpl(type, name, initialValue, this).also { visibleLocals.add(it) }
    }

    override fun createParameter(name: String, type: ResolvedType): ParameterRepresentation {
        assert(getLocal(name) == null) { "Already have local entity with name $name" }
        return ParameterImpl(type, name, this, parameters.size).also { visibleLocals.add(it) }
    }

    override fun createCallSite(
        invokedOn: RValueRepresentation?,
        callee: CallableRepresentation
    ): CallSiteRepresentation {
        val invocationExpression: InvocationExpression = if (invokedOn != null && callee is MethodRepresentation) {
            MethodInvocationExpressionImpl(callee, invokedOn)
        } else if (invokedOn == null && callee is FunctionRepresentation) {
            FunctionInvocationExpressionImpl(callee)
        } else if (invokedOn == null && callee is ConstructorRepresentation) {
            ObjectCreationExpressionImpl(callee)
        } else {
            throw Exception("unknown call site creation")
        }

        return CallSiteImpl(sites.size, this, invocationExpression).also { sites.add(it) }
    }

    override val terminationSite: TerminationSiteRepresentation = TerminationSiteImpl(this).also { sites.add(it) }
}

class ConstructorImpl(
    graphId: Int,
    override val visibility: VisibilityModifier,
    override val containingType: TypeRepresentation,
    override val parentConstructorCall: ObjectCreationExpression? = null,
    parameters: List<LValueRepresentation> = emptyList()
) : CallableImpl(graphId, containingType.instanceType, parameters), ConstructorRepresentation {
    override val returnType: ResolvedType
        get() = super<CallableImpl>.returnType

    override fun equals(other: Any?): Boolean {
        if (other !is ConstructorImpl) {
            return false
        }

        if (other.containingType != containingType) {
            return false
        }

        if (!super.equals(other)) {
            // we prohibit different constructors with the same signature
            assert(signature != other.signature)
            return false
        }

        return true
    }
}

open class FunctionImpl(
    graphId: Int,
    override val shortName: String,
    override val visibility: VisibilityModifier,
    returnType: ResolvedType,
    parameters: List<LValueRepresentation> = emptyList()
) : CallableImpl(graphId, returnType, parameters), FunctionRepresentation {

    override fun equals(other: Any?): Boolean {
        if (other !is FunctionImpl)
            return false

        if (graphId == other.graphId) {
            assert(this === other)
        } else {
            // all functions(including methods) should have unique <fqn, signatures>
            // and so 2 functions should not have same fqn and signature simultaneously
            assert(fqnName != other.fqnName || signature != other.signature)
        }

        return this === other
    }
}

class MethodImpl(
    name: String,
    visibility: VisibilityModifier,
    graphId: Int,
    returnType: ResolvedType,
    override val inheritanceModifier: InheritanceModifier,
    override val inheritedFrom: MethodImpl?,
    override val containingType: TypeRepresentation,
    override val overriddenMethod: MethodRepresentation? = null,
    parameters: List<LValueRepresentation> = emptyList()
) : FunctionImpl(graphId, name, visibility, returnType, parameters), MethodRepresentation

// endregion

// region expressions_impl

abstract class ArgumentsOwnerExpressionImpl : ArgumentsOwnerExpression {
    override val parameterToArgument = hashMapOf<ParameterRepresentation, RValueRepresentation>()
    override fun addInCall(parameter: ParameterRepresentation, argument: RValueRepresentation) {
        assert(!parameterToArgument.contains(parameter)) { "redeclaration of parameter value, do not do it!" }
        parameterToArgument[parameter] = argument
    }
}

class FunctionInvocationExpressionImpl(
    override val invokedCallable: FunctionRepresentation
) : ArgumentsOwnerExpressionImpl(), FunctionInvocationExpression

class MethodInvocationExpressionImpl(
    override val invokedMethod: MethodRepresentation,
    override val invokedOn: RValueRepresentation
) : ArgumentsOwnerExpressionImpl(), MethodInvocationExpression

class ObjectCreationExpressionImpl(override val invokedConstructor: ConstructorRepresentation) :
    ArgumentsOwnerExpressionImpl(), ObjectCreationExpression

// endregion

// region resolvedType_impl

abstract class ResolvedTypeImpl(final override val tag: TypeTag): ResolvedType {

}


class InstanceTypeImpl(final override val representation: TypeRepresentation): ResolvedTypeImpl(), InstanceType, NameOwner by representation {
    override fun furthestElementType(): InstanceType = this

    override fun wrapInArray(): ArrayType = ;
}

// endregion

// region values_impl

abstract class FunctionLocalImpl : CallableLocal {
    override fun hashCode(): Int {
        return parentCallable.hashCode() * 31 + shortName.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CallableLocal || parentCallable != other.parentCallable) {
            // there are no constraints on locals that refer to different callables
            return false
        }

        if (other !== this) {
            // locals in the same callable should have unique names
            assert(shortName != other.shortName)
        }

        return this === other
    }
}

class LocalVariableImpl(
    override val type: ResolvedType,
    // currently we prohibit shadowing local variables,
    // it means that local variables and parameters can be identified by its name and parent function
    override val shortName: String,
    override val initialValue: RValueRepresentation,
    override val parentCallable: CallableRepresentation
) : FunctionLocalImpl(), LocalVariableRepresentation

class ParameterImpl(
    override val type: ResolvedType,
    override val shortName: String,
    // invariant - two parameters relates to the same function if they point to the same function
    // currently we prohibit shadowing local variables,
    // it means that local variables and parameters can be identified by its name and parent function
    override val parentCallable: CallableRepresentation,
    // just for correct code generation
    override val indexInSignature: Int
) : ParameterRepresentation {
    override fun equals(other: Any?): Boolean {

        assert(
            // here we check that if this is 2 different parameters that refers to the same function --
            this === other
                    || other !is ParameterRepresentation
                    || parentCallable != other.parentCallable
                    // -- they stay on different indices
                    || indexInSignature != other.indexInSignature
        )
        // and here we check their names
        return super.equals(other)
    }
}

// endregion

class CodeRepresentation(private val graph: Map<Int, Set<Int>>, val language: TargetLanguage) {
    private val functions = mutableMapOf<Int, FunctionRepresentation>()
    private var startFunctionIdCounter = startFunctionFirstId
    private val startFunctionToGenericId = mutableMapOf<String, Int>()
    private val generatedTypes = mutableMapOf<String, TypeRepresentation>()

    // TODO types - int, unit, object, java.base
    fun dumpTo(sourcesDir: Path) {

    }

    fun getOrCreateFunctionFor(v: Int): FunctionRepresentation {
        return functions.getOrPut(v) { FunctionImpl(v) }
    }

    fun getOrCreateStartFunction(name: String): FunctionRepresentation {
        val startFunctionId = startFunctionToGenericId.getOrPut(name) { startFunctionIdCounter++ }

        return getOrCreateFunctionFor(startFunctionId)
    }

    fun getOrCreateReferenceType(name: String): TypeRepresentation {
        return generatedTypes.getOrPut(name) {
            TypeImpl(name, VisibilityModifier.PUBLIC, InheritanceModifier.FINAL)
        }
    }

    inline fun <reified T> getStandardType(): TypeRepresentation {
        //TODO correct
        return getOrCreateReferenceType(T::class.java.simpleName)
    }

    companion object {
        private const val startFunctionFirstId = Int.MAX_VALUE / 2
    }
}

interface TargetLanguage {
    fun resolveProjectPathToSources(projectPath: Path): Path
}

class JavaLanguage : TargetLanguage {
    override fun resolveProjectPathToSources(projectPath: Path): Path {
        TODO("Not yet implemented")
    }
}

interface AnalysisVulnerabilityProvider {
    fun provideInstance(codeRepresentation: CodeRepresentation): VulnerabilityInstance
    fun provideInstance(
        codeRepresentation: CodeRepresentation,
        block: VulnerabilityInstance.() -> Unit
    ): VulnerabilityInstance {
        return provideInstance(codeRepresentation).apply(block)
    }
}

class NpeProvider : AnalysisVulnerabilityProvider {
    override fun provideInstance(codeRepresentation: CodeRepresentation): VulnerabilityInstance {
        return NpeInstance(codeRepresentation)
    }
}

private var vulnerabilitiesCounter = 0

class NpeInstance(private val codeRepresentation: CodeRepresentation) : VulnerabilityInstance {
    private val id = "npeInstance${++vulnerabilitiesCounter}"
    private val realPath = mutableListOf<Int>()

    // все не что указано в тразите - идет в дефолт валуе. иначе берется из параметра.
    // так как рендеринг в конце - все будет ок
    // путь задаеися глобальным стейтом на доменах?))))
    // НЕТ! так как у нас проблема может быть только на одном путе, только пройдя его полностью - то нам не нужно эмулировать
    // диспатчинг в ифдс, он сам найдет только то, что нужно, а вот верификация будет за юсвм!!

    override fun createSource(u: Int) {
        val startFunction = codeRepresentation.getOrCreateStartFunction(id)
        val type = codeRepresentation.getOrCreateReferenceType(id)

        startFunction.newLocalVariable(type, id, type.defaultValue)
        transitVulnerability(startFunction.graphId, u)
        realPath.add(startFunction.graphId)
        realPath.add(u)
    }

    override fun mutateVulnerability(u: Int, v: Int) {
        // TODO currently do not mutate, enhance by time
    }

    override fun transitVulnerability(u: Int, v: Int) {
        val functionU = codeRepresentation.getOrCreateFunctionFor(u)
        val functionV = codeRepresentation.getOrCreateFunctionFor(v)
        val variableInU = functionU.getLocalEntity(id)!!
        val parameterInV = functionV.getOrCreateParameterFor(id, variableInU)
        val uvCallSite = functionU.getOrCreateCallSite(functionV)

        uvCallSite.addInCall(variableInU, parameterInV)
        realPath.add(v)
    }

    override fun createSink(v: Int) {
        val functionV = codeRepresentation.getOrCreateFunctionFor(v)
        val variableInV = functionV.getLocalEntity(id)!!
        val vTerminationSite = functionV.getOrCreateTerminationSite()

        vTerminationSite.addDereference(variableInV)
        dispatchPath()
    }

    private fun dispatchPath() {
        val startFunction = codeRepresentation.getOrCreateFunctionFor(realPath.first())
        val intArrayType = codeRepresentation.getStandardType<Array<Int>>()
        val dispatchArray = startFunction.newLocalVariable(
            intArrayType,
            "dispatchArray",
            intArrayType.valueRepresentationFrom(realPath)
        )
        val u = realPath[1]
        val functionU = codeRepresentation.getOrCreateFunctionFor(u)
        val parameterInU = functionU.getOrCreateParameterFor("dispatchArray", dispatchArray)
        val startUCallSite = startFunction.getOrCreateCallSite(functionU)

        startUCallSite.addInCall(dispatchArray, parameterInU)
    }
}

interface VulnerabilityInstance {
    fun createSource(u: Int)
    fun mutateVulnerability(u: Int, v: Int)
    fun transitVulnerability(u: Int, v: Int)
    fun createSink(v: Int)
}

private lateinit var randomer: Random

// TODO tests - generate by hands some tests, 100% cover must be
// TODO c++ implementation
// TODO plan him on following:
// TODO enums
// TODO complex representations - list of other
// TODO code element hierarchy and top/down
// TODO site representation hierarchy
// TODO dereferences, ifs, cycles, arrays, assignments, lambda invokes, returns
// TODO analyses aware constructors
// TODO interfaces - DAG, abstract classes - graph DFS, implementation of interfaces - zip dag to tree
// TODO method implementation - paths in tree divisino in half, random points
// TODO each call in graph path - may be virtual invoke, provide arguments on which this call should via generated hierarchy tree
// TODO generate data flow - first only simple returns and initial values in fields + tree types generation
// TODO then do complex reassignments with conditions
// TODO after that we can think of exceptions, lambdas, generics
// TODO final boss will be unsoundiness - reflection and jni
// TODO protected modifiers
// TODO type and/or signature correlation, covariance/contrvariance - this should be part of overloading

// can be added with minimal work, but i do not see usefulness in foreseeable future
// TODO extension methods? - should be functions/methods with additional mark
// TODO annotations? - tbh i dunno for what right now it might be required

// hard and involves much design and refactoring
// TODO accessors? - in some sense this should be method with some field reference. but not all languages support this, so skip for now
// TODO generics? templates? - oh fuk this is hard tbh


fun main(args: Array<String>) {
    assert(args.size == 5) {
        "vertices:Int edges:Int vulnerabilities:Int pathToProjectDir:String targetLanguage:String"
    }
    val n = args[0].toInt()
    val m = args[1].toInt()
    val k = args[2].toInt()

    randomer = Random(arrayOf(n, m, k).contentHashCode())

    val vulnerabilityProviderService = ServiceLoader.load(AnalysisVulnerabilityProvider::class.java)
    val vulnerabilityProviders = mutableListOf<AnalysisVulnerabilityProvider>()

    for (analysis in vulnerabilityProviderService) {
        vulnerabilityProviders.add(analysis)
    }

    assert(n in 1 until 1000) { "currently big graphs not supported just in case" }
    assert(m in 1 until 1000000) { "though we permit duplicated edges, do not overflow graph too much" }
    assert(k in 0 until min(listOf(255, n, m)))
    // 1. как-то задавать анализы, для которых генерируем граф
    // Это говно должено реализовывать интерфейс какой-нибудь, который должен быть положен рядом-в класспас,
    // мы его каким-нибудь рефлекшеном находим и радуемся
    // 2. как-то задавать файл путь в который че генерим
    // наверное хочу задавать путь до папки, в которую нужно класть проект. и да, туда сразу внутренности архива
    // 3. как-то завязать кеш дфс
    // просто реально держит ссылку на граф и просто мапчик да-нет и все
    // 6. нужна презентация реальных функций и че она умеет
    // функция - название, параметры, у параметра тип, и пишется он явно в формате джавы(другой язык мб потом)
    // также функция имеет понимание, в каком порядке какие вызовы в ней будут делаться, и какие у каждого вызова параметры и в каком порядке
    // изменения каждого параметра производятся перед самым вызовом в пути, тем самым гарантируем, что там не будут важны предыдущие значения
    // также из этого следует, что мы не можем двумя разными способами вызываться в одном методе.
    // !!!проблема - мы практически точно будет генерировать бесконечные рекурсии при любом цикле!!!
    // то есть мы гарантированно должны быть ациклически! для этого будет использоваться стек, в который будет положен какое ребро нужно вызвать
    // на данный момент мы поддерживаем явный путь в графе, но никак не "исполнение"(то есть как бы историю работы дфс).
    // 4. как-то сделать реализацию vulnerabilities итдитп
    // ну наверное ему нужен стартовая вершина, конечная, естественное весь путь, также функциональная репрезентация каждого говна,
    // и каждый такой анализ дожен по этому путь пройтись и сам что-то сделать так, чтобы ничего не сломать остальным

    // 5. сделать дампалку итогового состояния функций через жопу
    // просто интерфейс, который принимает функцию из репрезентации и путь, куда это говно надо написать. Дальше уже разбирается сама.
    // их тоже можно искать сервисом
    val projectPath = Paths.get(args[3]).normalize()
    assert(projectPath.notExists() || projectPath.useDirectoryEntries { it.none() }) { "Provide path to directory which either does not exists or empty" }
    val targetLanguageString = args[4]
    val targetLanguageService = ServiceLoader.load(TargetLanguage::class.java)
    val targetLanguage = targetLanguageService.single { it.javaClass.simpleName == targetLanguageString }

    val dezipper = DeZipper()

    dezipper.dezip(projectPath)

    val graph = mutableMapOf<Int, MutableSet<Int>>()

    for (i in 0 until m) {
        val u = Random.nextInt(n)
        val v = Random.nextInt(n)
        // TODO loops v->v?
        graph.getOrPut(u) { mutableSetOf() }.add(v)
    }

    val accessibilityCache = AccessibilityCache(graph)
    val codeRepresentation = CodeRepresentation(graph, targetLanguage)
    val generatedVulnerabilitiesList = mutableListOf<VulnerabilityInstance>()

    for (i in 0 until k) {
        val u = Random.nextInt(n)
        val v = Random.nextInt(n)
        val vulnerabilityIndex = Random.nextInt(vulnerabilityProviders.size)
        val vulnerabilityProvider = vulnerabilityProviders[vulnerabilityIndex]

        if (accessibilityCache.isAccessible(u, v)) {
            val path = accessibilityCache.getAccessPath(u, v)
            val instance = vulnerabilityProvider.provideInstance(codeRepresentation) {
                createSource(u)
                for (j in 0 until path.lastIndex) {
                    val startOfEdge = path[j]
                    val endOfEdge = path[j + 1]
                    mutateVulnerability(startOfEdge, endOfEdge)
                    transitVulnerability(startOfEdge, endOfEdge)
                }
                createSink(v)
            }
            generatedVulnerabilitiesList.add(instance)
        }
    }

    val srcDir = targetLanguage.resolveProjectPathToSources(projectPath)
    codeRepresentation.dumpTo(srcDir)
}