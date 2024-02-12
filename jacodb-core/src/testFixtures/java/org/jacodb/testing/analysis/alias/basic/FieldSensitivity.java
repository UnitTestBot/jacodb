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

package org.jacodb.testing.analysis.alias.basic;

import org.jacodb.testing.analysis.alias.internal.TestUtil;

public class FieldSensitivity {
    public static class A {
        Object o;
    }

    private static void assign(A x, A y) {
        y.o = x.o;
    }

    public void testFieldSensitivity() {
        Object o = TestUtil.alloc(1);
        A x = new A();
        x.o = o;
        A y = new A();
        assign(x, y);
        TestUtil.check(
            "y.o",
            new String[]{"y.o", "o", "x.o"},
            new String[]{"x", "y"},
            new int[]{1}
        );
    }
}
