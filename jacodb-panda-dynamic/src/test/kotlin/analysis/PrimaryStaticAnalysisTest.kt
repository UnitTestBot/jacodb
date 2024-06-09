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

package analysis

import org.jacodb.panda.dynamic.api.PandaProject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import panda.primary.*
import parser.loadIr

class PrimaryStaticAnalysisTest {
    private fun getProjectByProgramName(programName: String): PandaProject {
        val parser = loadIr("/samples/${programName}.json")
        return parser.getProject()
    }

    @Nested
    inner class ArgumentParameterCorrespondenceTest {
        private val programName = "codeqlSamples/parametersArgumentsMismatch"
        private val project = getProjectByProgramName(programName)
        private val analyser = ArgumentParameterMatchingChecker(project)

        @Test
        fun `test for mismatch detection in regular function call`() {
            val mismatches = analyser.analyseOneCase(
                startMethods = listOf("foo")
            )
            assert(mismatches.size == 2)
        }

        @Test
        fun `test for mismatch detection in call of function with default arguments 1`() {
            val mismatches = analyser.analyseOneCase(
                startMethods = listOf("foo1")
            )
            assert(mismatches.size == 1)
        }

        @Test
        fun `test for mismatch detection in call of function with default arguments 2`() {
            val mismatches = analyser.analyseOneCase(
                startMethods = listOf("foo2")
            )
            assert(mismatches.isEmpty())
        }
        @Disabled("reconcile arguments number for class methods (is 'this' count?)")
        @Test
        fun `positive example - test for mismatch detection in class method call`() {
            val mismatches = analyser.analyseOneCase(
                startMethods = listOf("rightUsage")
            )
            assert(mismatches.isEmpty())
        }

        @Disabled("Don't work cause we can't resolve constructors yet")
        @Test
        fun `counterexample - test for mismatch detection in class method call`() {
            val mismatches = analyser.analyseOneCase(
                startMethods = listOf("wrongUsage")
            )
            assert(mismatches.size == 3)
        }
    }

    @Nested
    inner class UnresolvedVariableTest {
        private fun getAnalyserByProgramName(programName: String): UndeclaredVariablesChecker {
            val project = getProjectByProgramName(programName)
            val analyser = UndeclaredVariablesChecker(project)
            return analyser
        }

        @Test
        fun `counterexample - program that read some unresolved variables`() {
            val analyser = getAnalyserByProgramName("codeqlSamples/unresolvedVariable")
            val unresolvedVariables = analyser.analyse()
            assert(unresolvedVariables.size == 4)
        }

        @Test
        fun `counterexample - program that also write into unresolved variables`() {
            val analyser = getAnalyserByProgramName("codeqlSamples/unresolvedVariable2")
            val unresolvedVariables = analyser.analyse()
            assert(unresolvedVariables.size == 3)
        }

        @Test
        fun `counterexample - program that read undeclared const variables`() {
            val analyser = getAnalyserByProgramName("codeqlSamples/unresolvedVariable3")
            val unresolvedVariables = analyser.analyse()
            assert(unresolvedVariables.size == 3)
        }
    }

    @Nested
    inner class ImplicitCastingTest {
        private val programName = "codeqlSamples/implicitCasting"
        private val project = getProjectByProgramName(programName)
        private val analyser = ImplicitCastingChecker(project)

        @Test
        fun `test implicit casting observation in binary expressions with primitive literals`() {
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("primitiveLiterals")
            )
            assert(typeMismatches.size == 10)
        }

        @Disabled("No type support for arrays and objects")
        @Test
        fun `test implicit casting observation in binary expressions with complex literals`() {
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("complexLiterals")
            )
            assert(typeMismatches.size == 4)
        }

        @Test
        fun `test implicit casting checking in binary expressions with strings`() {
            val impossibleImplicitCasts = analyser.analyseOneCase(
                startMethods = listOf("binaryOperationsWithStrings"),
                mode = ImplicitCastAnalysisMode.POSSIBILITY_CHECK
            )
            assert(impossibleImplicitCasts.size == 8)
        }

        @Disabled("Not supported yet")
        @Test
        fun `complicate test implicit casting checking in binary expressions with strings`() {
            val impossibleImplicitCasts = analyser.analyseOneCase(
                startMethods = listOf("binaryOperationsWithStrings2"),
                mode = ImplicitCastAnalysisMode.POSSIBILITY_CHECK
            )
            assert(impossibleImplicitCasts.size == 2)
        }
    }

    @Nested
    inner class MissingMembersTest {
        private fun getAnalyserByProgramName(programName: String): MissingMembersChecker {
            val project = getProjectByProgramName(programName)
            val analyser = MissingMembersChecker(project)
            return analyser
        }

        @Test
        fun `test calling members on primitive types`() {
            val analyser = getAnalyserByProgramName("codeqlSamples/missingMembers")
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("callingMembersOnPrimitiveTypes")
            )
            assert(typeMismatches.size == 6)
        }

        @Disabled("createarraywithbuffer not supported yet cause have no information about its elements in IR")
        @Test
        fun `test calling members on non object types`() {
            val analyser = getAnalyserByProgramName("codeqlSamples/missingMembers")
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("callingMembersOnNonObjectsTypes")
            )
            assert(typeMismatches.size == 4)
        }

        @Test
        fun `test calling members on null`() {
            val analyser = getAnalyserByProgramName("codeqlSamples/missingMembers")
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("callingMembersOnNullType")
            )
            assert(typeMismatches.size == 2)
        }

        @Disabled("objects weakly supported right now both in IR and in parser")
        @Test
        fun `test calling members on objects`() {
            val analyser = getAnalyserByProgramName("codeqlSamples/missingMembers")
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("callingMembersOnObjects")
            )
            assert(typeMismatches.isEmpty())
        }

        @Test
        fun `test calling methods on class instance`() {
            val analyser = getAnalyserByProgramName("codeqlSamples/missingMembers2")
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("callingMethods")
            )
            assert(typeMismatches.size == 1)
        }

        @Disabled("IRParser doesn't track information about properties and IR have no information about uninitialized properties")
        @Test
        fun `test accessing properties on class instance`() {
            val analyser = getAnalyserByProgramName("codeqlSamples/missingMembers2")
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("accessingProperties")
            )
            assert(typeMismatches.size == 4)
        }

        @Disabled("IR have no information about superclasses so lack knowledge about not overrided members")
        @Test
        fun `test on child instance calling methods defined in parent class and not overrided after that in child`() {
            val analyser = getAnalyserByProgramName("classes/InheritanceClass")
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("func_main_0")
            )
            assert(typeMismatches.isEmpty())
        }

        @Test
        fun `test calling static methods`() {
            val analyser = getAnalyserByProgramName("classes/StaticClass")
            val typeMismatches = analyser.analyseOneCase(
                startMethods = listOf("func_main_0")
            )
            assert(typeMismatches.isEmpty())
        }
    }
}
