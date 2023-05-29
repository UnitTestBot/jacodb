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

package org.jacodb.testing.types;

import org.jacodb.testing.KotlinNullabilityExamples;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NullAnnotationExamples {
    String refNullable;
    @NotNull String refNotNull = "dumb init value";
    @Nullable String explicitlyNullable;

    int primitiveValue;

    public static class SomeContainer<E> {
        public List<@NotNull E> listOfNotNull;
        public List<@Nullable E> listOfNullable;
        public List<E> listOfUndefined;

        public @NotNull E notNull;
        public @Nullable E nullable;
        public E undefined;

        public class Inner {}
    }

    String nullableMethod(@Nullable String explicitlyNullableParam, @NotNull String notNullParam, List<@NotNull String> notNullContainer) {
        return null;
    }

    @NotNull String notNullMethod(@Nullable String explicitlyNullableParam, @NotNull String notNullParam) {
        return "dumb return value";
    }

    public @Nullable SomeContainer<? extends @NotNull String> wildcard() {
        return null;
    }

    public SomeContainer<@NotNull String>.@Nullable Inner inner() {
        return null;
    }

    public @NotNull SomeContainer<String> @Nullable[] array() {
        return null;
    }

    public SomeContainer<@NotNull String> containerOfNotNull;
    public SomeContainer<@Nullable String> containerOfNullable;
    public SomeContainer<String> containerOfUndefined;

    public KotlinNullabilityExamples.SomeContainer<@NotNull String> ktContainerOfNotNull;
    public KotlinNullabilityExamples.SomeContainer<@Nullable String> ktContainerOfNullable;
    public KotlinNullabilityExamples.SomeContainer<String> ktContainerOfUndefined;
}