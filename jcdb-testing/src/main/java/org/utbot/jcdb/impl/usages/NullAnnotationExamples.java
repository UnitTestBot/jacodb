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

package org.utbot.jcdb.impl.usages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NullAnnotationExamples {
    String refNullable;
    @NotNull String refNotNull = "dumb init value";
    @Nullable String explicitlyNullable;

    int primitiveValue;

    String nullableMethod(@Nullable String explicitlyNullableParam, @NotNull String notNullParam, List<@NotNull String> notNullContainer) {
        return null;
    }

    @NotNull String notNullMethod(@Nullable String explicitlyNullableParam, @NotNull String notNullParam) {
        return "dumb return value";
    }

    public SomeContainer<? extends String> kek() {
        return null;
    }

//    public KotlinNullabilityExamples.SomeContainer<String> kek2() { return null; }
//
//    public KotlinNullabilityExamples.SomeContainer<@Nullable String> ktContainer;

    public static class SomeContainer<E> {
        public E undefined = null;
        public @NotNull E notNull;
        public @Nullable E nullable;

        public List<E> listOfUndefined;
        public List<@NotNull E> listOfNotNull;
        public List<@Nullable E> listOfNullable;
    }
//    public SomeContainer<@NotNull String> javaContainer;

//    public void kek3() {
//        if (javaContainer.undefined == null) {
//            // STATIC ANALYZER FAIL HAHA
//        }
//    }

    public void instantiatedContainer(SomeContainer<String> nullableParam, SomeContainer<@NotNull String> notNullParam) {}
}