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

import java.util.List;

public class WildcardBounds {

    public static class DirectBound<T> {
        public List<T> field;
    }

    public static class WildcardUpperBound<T> {
        public List<? extends T> field;
    }

    public static class WildcardUpperBoundString extends WildcardUpperBound<String> {
    }

    public static class WildcardLowerBoundString extends WildcardLowerBound<String> {
    }

    public static class DirectBoundString extends DirectBound<String> {
    }

    public static class WildcardLowerBound<T> {

        public List<? super T> field;

        public List<T> method(List<T> input) {
            return null;
        }
    }
}
