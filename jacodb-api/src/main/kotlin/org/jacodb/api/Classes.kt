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
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

interface JcClassOrInterface : JcAnnotatedSymbol, JcAccessible {

    val classpath: JcClasspath

    val declaredFields: List<JcField>
    val declaredMethods: List<JcMethod>

    val simpleName: String
    val signature: String?
    val isAnonymous: Boolean

    fun bytecode(): ClassNode
    fun binaryBytecode(): ByteArray

    val superClass: JcClassOrInterface?
    val outerMethod: JcMethod?
    val outerClass: JcClassOrInterface?
    val interfaces: List<JcClassOrInterface>
    val innerClasses: List<JcClassOrInterface>

    fun <T> extensionValue(key: String): T?
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

    val exceptions: List<JcClassOrInterface>

    fun body(): MethodNode
    fun flowGraph(): JcGraph

    val rawInstList: JcInstList<JcRawInst>
    val instList: JcInstList<JcInst>

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
