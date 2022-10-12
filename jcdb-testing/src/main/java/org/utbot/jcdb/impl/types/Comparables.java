package org.utbot.jcdb.impl.types;

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

        @Override
        public int compareTo(@NotNull ComparableTest3 o) {
            return 0;
        }
    }



}
