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

//package org.jacodb.analysis.alias.apg
//
//import org.jacodb.api.cfg.JcLocalVar
//import org.jacodb.api.ext.findClass
//import org.jacodb.api.ext.findDeclaredMethodOrNull
//import org.jacodb.api.ext.objectType
//import org.jacodb.testing.BaseTest
//import org.jacodb.testing.WithDB
//import org.junit.jupiter.api.Test
//
//class Test : BaseTest() {
//    companion object : WithDB()
//
//    @Test
//    fun testApg() {
//        val clazz = cp.findClass("")
//        val method = clazz.findDeclaredMethodOrNull("aliasOnAssignment")
//
//        println
//
//        val localVar = JcLocalVar("idiot", cp.objectType)
//        val apg = accessGraphOf(localVar)
//        println(apg)
//    }
//}