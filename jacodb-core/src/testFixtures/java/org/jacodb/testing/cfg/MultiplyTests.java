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

package org.jacodb.testing.cfg;

import java.math.BigDecimal;

public class MultiplyTests {

    private static int multiplyTests() {
        int failures = 0;

        BigDecimal[] bd1 = {
                new BigDecimal("123456789"),
        };

        BigDecimal[] bd2 = {
                new BigDecimal("987654321"),
        };

        // Two dimensonal array recording bd1[i] * bd2[j] &
        // 0 <= i <= 2 && 0 <= j <= 2;
        BigDecimal[][] expectedResults = {
                {new BigDecimal("121932631112635269"),
                },
                {new BigDecimal("1219326319027587258"),
                },
                {new BigDecimal("12193263197189452827"),
                }
        };

        for (int i = 0; i < bd1.length; i++) {
            for (int j = 0; j < bd2.length; j++) {
                if (!bd1[i].multiply(bd2[j]).equals(expectedResults[i][j])) {
                    failures++;
                }
            }
        }
        return failures;
    }

    public static Object test() {

        int failures = 0;

        failures += multiplyTests();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures +
                    " failures while testing multiply.");
        }
        System.out.println("OK");
        return null;
    }
}
