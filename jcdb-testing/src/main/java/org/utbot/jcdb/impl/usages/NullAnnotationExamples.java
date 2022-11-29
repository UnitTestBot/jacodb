package org.utbot.jcdb.impl.usages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NullAnnotationExamples {
    String refNullable;
    @NotNull String refNotNull = "dumb init value";

    int primitiveValue;

    String nullableMethod(@Nullable String explicitlyNullableParam, @NotNull String notNullParam) {
        return null;
    }

    @NotNull String notNullMethod(@Nullable String explicitlyNullableParam, @NotNull String notNullParam) {
        return "dumb return value";
    }
}