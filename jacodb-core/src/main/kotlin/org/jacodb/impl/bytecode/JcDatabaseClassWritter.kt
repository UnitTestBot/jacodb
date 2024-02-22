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

package org.jacodb.impl.bytecode

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.findClass
import org.objectweb.asm.ClassWriter


/**
 * ASM class writer with jacodb specific resolution of common superclasses
 */
class JcDatabaseClassWriter(val classpath: JcClasspath, flags: Int) : ClassWriter(flags) {

    /*
   * We need to overwrite this method here, as we are generating multiple classes that might reference each other. See
   * asm4-guide, top of page 45 for more information.
   *
   * @see org.objectweb.asm.ClassWriter#getCommonSuperClass(java.lang.String, java.lang.String)
   */
    override fun getCommonSuperClass(type1: String, type2: String): String {
        val typeName1 = type1.replace('/', '.')
        val typeName2 = type2.replace('/', '.')
        val jcClass1 = classpath.findClass(typeName1)
        val jcClass2 = classpath.findClass(typeName2)
        val super1 = jcClass1.allSuperClasses
        val super2 = jcClass2.allSuperClasses

        // If these two classes haven't been loaded yet or are phantom, we take
        // java.lang.Object as the common superclass
        return when {
            super1.isEmpty() || super2.isEmpty() -> "java/lang/Object"
            else -> {
                super1.firstOrNull { super2.contains(it) }?.name?.replace(".", "/")
                    ?: throw RuntimeException("Could not find common super class for $type1 and $type2")
            }

        }
    }

    private val JcClassOrInterface.allSuperClasses: List<JcClassOrInterface>
        get() {
            val result = arrayListOf<JcClassOrInterface>()
            var jcClass = superClass
            while (jcClass != null) {
                result.add(jcClass)
                jcClass = jcClass.superClass
            }
            return result
        }
}
