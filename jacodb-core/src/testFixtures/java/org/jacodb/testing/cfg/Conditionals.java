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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Conditionals {
    void main(int x, int y) {
        if (x < 0) {
            System.out.println("< 0");
        }
        if (x <= 0) {
            System.out.println("<= 0");
        }
        if (x < y) {
            System.out.println("<");
        }
        if (x <= y) {
            System.out.println("<=");
        }
    }

    public static void conditionInFor() {
        Random rnd = new Random();
        List<Boolean> list = new ArrayList<>();
        int numFalse = 0;
        for (int i = 0; i < 1000; i++) {
            boolean element = rnd.nextBoolean();
            if (!element)
                numFalse++;
            list.add(element);
        }

        Collections.sort(list);

        for (int i = 0; i < numFalse; i++)
            if (list.get(i))
                throw new RuntimeException("False positive: " + i);
        for (int i = numFalse; i < 1000; i++)
            if (!list.get(i))
                throw new RuntimeException("False negative: " + i);
    }

}
