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

package org.utbot.jacodb.impl.types;

import org.jetbrains.annotations.NotNull;

public class Comparables {

    public static class ComparableTest1 implements Comparable<ComparableTest1> {
        @Override
        public int compareTo(@NotNull ComparableTest1 o) {
            return 0;
        }
    }

    public static class ComparableTest2<T extends Comparable<T>> implements Comparable<T> {

        @Override
        public int compareTo(@NotNull T o) {
            return 0;
        }
    }

    public static class ComparableTest3 extends ComparableTest2<ComparableTest3> {

    }

    public static class ComparableTest4<T extends Comparable<W>, W extends Comparable<T>> extends ComparableTest2<ComparableTest3> {
        W stateW;
        T stateT;
    }

    public static class ComparableTest5 extends ComparableTest4<Integer, Integer> {
    }

}
