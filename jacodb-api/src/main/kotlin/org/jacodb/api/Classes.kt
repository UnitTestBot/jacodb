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

package org.jacodb.api

import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.api.ext.CONSTRUCTOR
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

@JvmDefaultWithoutCompatibility
interface JcClassOrInterface : JcAnnotatedSymbol, JcAccessible {

    val classpath: JcClasspath

    val declaredFields: List<JcField>
    val declaredMethods: List<JcMethod>

    val simpleName: String
    val signature: String?
    val isAnonymous: Boolean

    fun asmNode(): ClassNode
    fun bytecode(): ByteArray

    val superClass: JcClassOrInterface?
    val outerMethod: JcMethod?
    val outerClass: JcClassOrInterface?
    val interfaces: List<JcClassOrInterface>
    val innerClasses: List<JcClassOrInterface>

    fun <T> extensionValue(key: String): T?

    /**
     * lookup instance for this class. Use it to resolve field/method references from bytecode instructions
     *
     * It's not necessary that looked up method will return instance preserved in [JcClassOrInterface.declaredFields] or
     * [JcClassOrInterface.declaredMethods] collections
     */
    val lookup: JcLookup<JcField, JcMethod>

    val isAnnotation: Boolean
        get() {
            return access and Opcodes.ACC_ANNOTATION != 0
        }

    /**
     * is class is interface
     */
    val isInterface: Boolean
        get() {
            return access and Opcodes.ACC_INTERFACE != 0
        }


}

interface JcAnnotation : JcSymbol {

    val visible: Boolean
    val jcClass: JcClassOrInterface?

    val values: Map<String, Any?>

    fun matches(className: String): Boolean

}

interface JcMethod : JcSymbol, JcAnnotatedSymbol, JcAccessible {

    /** reference to class */
    val enclosingClass: JcClassOrInterface

    val description: String

    val returnType: TypeName

    val signature: String?
    val parameters: List<JcParameter>

    val exceptions: List<TypeName>

    fun asmNode(): MethodNode
    fun flowGraph(): JcGraph

    val rawInstList: JcInstList<JcRawInst>
    val instList: JcInstList<JcInst>

    /**
     * is method has `native` modifier
     */
    val isNative: Boolean
        get() {
            return access and Opcodes.ACC_NATIVE != 0
        }

    /**
     * is item has `synchronized` modifier
     */
    val isSynchronized: Boolean
        get() {
            return access and Opcodes.ACC_SYNCHRONIZED != 0
        }

    /**
     * return true if method is constructor
     */
    val isConstructor: Boolean
        get() {
            return name == CONSTRUCTOR
        }

    val isClassInitializer: Boolean
        get() {
            return name == "<clinit>"
        }

}

interface JcField : JcAnnotatedSymbol, JcAccessible {

    val enclosingClass: JcClassOrInterface
    val type: TypeName
    val signature: String?
}

interface JcParameter : JcAnnotated, JcAccessible {
    val type: TypeName
    val name: String?
    val index: Int
    val method: JcMethod
}

interface TypeName {
    val typeName: String
}

