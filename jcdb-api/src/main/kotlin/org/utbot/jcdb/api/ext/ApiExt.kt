package org.utbot.jcdb.api

import kotlinx.collections.immutable.toPersistentList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import org.utbot.jcdb.api.ext.findClassOrNull
import org.utbot.jcdb.api.ext.findTypeOrNull
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Long
import java.lang.Short

/**
 * is item has `public` modifier
 */
val JcAccessible.isPublic: Boolean
    get() {
        return access and Opcodes.ACC_PUBLIC != 0
    }

/**
 * is item has `private` modifier
 */
val JcAccessible.isPrivate: Boolean
    get() {
        return access and Opcodes.ACC_PRIVATE != 0
    }

/**
 * is item has `protected` modifier
 */
val JcAccessible.isProtected: Boolean
    get() {
        return access and Opcodes.ACC_PROTECTED != 0
    }

/**
 * is item has `package` modifier
 */
val JcAccessible.isPackagePrivate: Boolean
    get() {
        return !isPublic && !isProtected && !isPrivate
    }

/**
 * is item has `static` modifier
 */
val JcAccessible.isStatic: Boolean
    get() {
        return access and Opcodes.ACC_STATIC != 0
    }

/**
 * is item has `final` modifier
 */
val JcAccessible.isFinal: Boolean
    get() {
        return access and Opcodes.ACC_FINAL != 0
    }

/**
 * is item has `synchronized` modifier
 */
val JcMethod.isSynchronized: Boolean
    get() {
        return access and Opcodes.ACC_SYNCHRONIZED != 0
    }

/**
 * is item has `volatile` modifier
 */
val JcField.isVolatile: Boolean
    get() {
        return access and Opcodes.ACC_VOLATILE != 0
    }

/**
 * is field has `transient` modifier
 */
val JcField.isTransient: Boolean
    get() {
        return access and Opcodes.ACC_TRANSIENT != 0
    }

/**
 * is method has `native` modifier
 */
val JcMethod.isNative: Boolean
    get() {
        return access and Opcodes.ACC_NATIVE != 0
    }

/**
 * is class is interface
 */
val JcClassOrInterface.isInterface: Boolean
    get() {
        return access and Opcodes.ACC_INTERFACE != 0
    }

/**
 * is item has `abstract` modifier
 */
val JcAccessible.isAbstract: Boolean
    get() {
        return access and Opcodes.ACC_ABSTRACT != 0
    }

/**
 * is method has `strictfp` modifier
 */
val JcMethod.isStrict: Boolean
    get() {
        return access and Opcodes.ACC_STRICT != 0
    }

/**
 * return true if method is constructor
 */
val JcMethod.isConstructor: Boolean
    get() {
        return name == "<init>"
    }

val JcAccessible.isSynthetic: Boolean
    get() {
        return access and Opcodes.ACC_SYNTHETIC != 0
    }

fun JcClassOrInterface.isLocalOrAnonymous(): Boolean {
    return outerMethod != null
}

fun JcClassOrInterface.isLocal(): Boolean {
    return outerClass != null && !isAnonymous
}

fun JcClassOrInterface.isMemberClass(): Boolean {
    return simpleBinaryName() != null && !isLocalOrAnonymous()
}

fun JcClassOrInterface.isEnum(): Boolean {
    return access and Opcodes.ACC_ENUM != 0 && superClass?.name == Enum::class.java.name
}

private fun JcClassOrInterface.simpleBinaryName(): String? {
    // top level class
    val enclosingClass = outerClass ?: return null
    // Otherwise, strip the enclosing class' name
    return try {
        name.substring(enclosingClass.name.length)
    } catch (ex: IndexOutOfBoundsException) {
        throw InternalError("Malformed class name", ex)
    }
}

/**
 * @return element class in case of `this` is ArrayClass
 */
fun JcType.ifArrayGetElementClass(): JcType? {
    return when (this) {
        is JcArrayType -> elementType
        else -> null
    }
}

/**
 * unboxes `this` class. That means that for 'java.lang.Integet' will be returned `PredefinedPrimitive.int`
 * and for `java.lang.String` will be returned `java.lang.String`
 */
fun JcType.unboxIfNeeded(): JcType {
    return when (typeName) {
        "java.lang.Boolean" -> classpath.boolean
        "java.lang.Byte" -> classpath.byte
        "java.lang.Char" -> classpath.char
        "java.lang.Short" -> classpath.short
        "java.lang.Integer" -> classpath.int
        "java.lang.Long" -> classpath.long
        "java.lang.Float" -> classpath.float
        "java.lang.Double" -> classpath.double
        else -> this
    }
}

/**
 * unboxes `this` class. That means that for 'java.lang.Integet' will be returned `PredefinedPrimitive.int`
 * and for `java.lang.String` will be returned `java.lang.String`
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
suspend fun JcType.autoboxIfNeeded(): JcType {
    return when (this) {
        classpath.boolean -> classpath.findTypeOrNull<java.lang.Boolean>() ?: throwClassNotFound<java.lang.Boolean>()
        classpath.byte -> classpath.findTypeOrNull<java.lang.Byte>() ?: throwClassNotFound<Byte>()
        classpath.char -> classpath.findTypeOrNull<Character>() ?: throwClassNotFound<Character>()
        classpath.short -> classpath.findTypeOrNull<java.lang.Short>() ?: throwClassNotFound<Short>()
        classpath.int -> classpath.findTypeOrNull<Integer>() ?: throwClassNotFound<Integer>()
        classpath.long -> classpath.findTypeOrNull<java.lang.Long>() ?: throwClassNotFound<Long>()
        classpath.float -> classpath.findTypeOrNull<java.lang.Float>() ?: throwClassNotFound<Float>()
        classpath.double -> classpath.findTypeOrNull<java.lang.Double>() ?: throwClassNotFound<Double>()
        else -> this
    }
}

/**
 * @return all interfaces and classes retrieved recursively from this ClassId
 */
suspend fun JcClassOrInterface.forEachSuperClasses(action: suspend (JcClassOrInterface) -> Unit): List<JcClassOrInterface> {
    val parents = (interfaces + superClass).filterNotNull()
    parents.forEach {
        action(it)
    }
    val result = parents.toMutableSet()
    parents.forEach {
        it.forEachSuperClasses(action)
    }
    return result.toPersistentList()
}

suspend fun JcClassOrInterface.findAllSuperClasses(): List<JcClassOrInterface> {
    val result = hashSetOf<JcClassOrInterface>()
    forEachSuperClasses {
        result.add(it)
    }
    return result.toPersistentList()
}

suspend infix fun JcClassOrInterface.isSubtypeOf(another: JcClassOrInterface): Boolean {
    if (another == classpath.findClassOrNull<Any>()) {
        return true
    }
    return another in findAllSuperClasses()
}


/**
 * find field by name
 */
fun JcClassOrInterface.findFieldOrNull(name: String): JcField? = fields.firstOrNull { it.name == name }

/**
 * find method by name and description
 */
fun JcClassOrInterface.findMethodOrNull(name: String, desc: String): JcMethod? =
    methods.firstOrNull { it.name == name && it.description == desc }

/**
 * find method by ASM node
 */
fun JcClassOrInterface.findMethodOrNull(methodNode: MethodNode): JcMethod? =
    methods.firstOrNull { it.name == methodNode.name && it.description == methodNode.desc }


/**
 * @return null if ClassId is not enum and enum value names otherwise
 */
fun JcClassOrInterface.enumValues(): List<JcField>? {
    if (isEnum()) {
        return fields.filter { it.isStatic && it.type.typeName == name }
    }
    return null
}


fun JcClassOrInterface.allMethods(): List<JcMethod> {
    var clazz: JcClassOrInterface? = this@allMethods
    val result = arrayListOf<JcMethod>()
    while (clazz != null) {
        result.addAll(clazz.methods.filter { !it.isConstructor })
        clazz = clazz.superClass
    }
    return result
}

val JcClassOrInterface.allConstructors: List<JcMethod>
    get() {
        return methods.filter { it.isConstructor }
    }

fun String.jvmName(): String {
    return when {
        this == PredefinedPrimitives.boolean -> "Z"
        this == PredefinedPrimitives.byte -> "B"
        this == PredefinedPrimitives.char -> "C"
        this == PredefinedPrimitives.short -> "S"
        this == PredefinedPrimitives.int -> "I"
        this == PredefinedPrimitives.float -> "F"
        this == PredefinedPrimitives.long -> "J"
        this == PredefinedPrimitives.double -> "D"
        this == PredefinedPrimitives.void -> "V"
        endsWith("[]") -> {
            val elementName = substring(0, length - 2)
            "[" + elementName.jvmName()
        }

        else -> "L$this;"
    }
}

fun String.jcdbName(): String {
    return when {
        this == "Z" -> PredefinedPrimitives.boolean
        this == "B" -> PredefinedPrimitives.byte
        this == "C" -> PredefinedPrimitives.char
        this == "S" -> PredefinedPrimitives.short
        this == "I" -> PredefinedPrimitives.int
        this == "F" -> PredefinedPrimitives.float
        this == "J" -> PredefinedPrimitives.long
        this == "D" -> PredefinedPrimitives.double
        this == "V" -> PredefinedPrimitives.void
        startsWith("[") -> {
            val elementName = substring(1, length)
            elementName.jcdbName() + "[]"
        }

        startsWith("L") -> {
            substring(1, length - 1)
        }

        else -> this
    }
}


val JcMethod.jvmSignature: String
    get() {
        return name + description
    }

val JcMethod.jcdbSignature: String
    get() {
        val params = parameters.joinToString(";") { it.type.typeName } + (";".takeIf { parameters.isNotEmpty() } ?: "")
        return "$name($params)${returnType.typeName};"
    }

const val NotNull = "org.jetbrains.annotations.NotNull"

val JcMethod.isNullable: Boolean
    get() {
        return annotations.any { it.matches(NotNull) }
    }

val JcField.isNullable: Boolean
    get() {
        return annotations.any { it.matches(NotNull) }
    }

val JcParameter.isNullable: Boolean
    get() {
        return annotations.any { it.matches(NotNull) }
    }

fun JcClasspath.anyType(): JcClassType =
    findTypeOrNull("java.lang.Object") as? JcClassType ?: throwClassNotFound<Any>()


fun JcClassOrInterface.toType(): JcClassType {
    return classpath.typeOf(this) as JcClassType
}

val JcClassOrInterface.packageName get() = name.substringBeforeLast(".")