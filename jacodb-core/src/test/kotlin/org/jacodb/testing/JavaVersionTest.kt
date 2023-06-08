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

package org.jacodb.testing

import org.jacodb.impl.JcSettings
import org.jacodb.impl.fs.JavaRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE

class JavaVersionTest {

    @Test
    @EnabledOnJre(JRE.JAVA_11)
    fun `java version should be proper for 11 java`() {
        assertEquals(11, JavaRuntime(JcSettings().useProcessJavaRuntime().jre).version.majorVersion)
    }
    @Test
    @EnabledOnJre(JRE.JAVA_8)
    fun `java version should be proper for 8 java`() {
        assertEquals(8, JavaRuntime(JcSettings().useProcessJavaRuntime().jre).version.majorVersion)
    }

}