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