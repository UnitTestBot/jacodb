package org.jacodb.analysis.alias.basic

import org.jacodb.analysis.alias.BaseAliasTest
import org.jacodb.testing.analysis.alias.basic.FieldSensitivity
import org.junit.jupiter.api.TestFactory

class FieldSensitivityTest : BaseAliasTest(FieldSensitivity::class) {
    @TestFactory
    fun testSample() =
        testMethod(FieldSensitivity::testFieldSensitivity)
}