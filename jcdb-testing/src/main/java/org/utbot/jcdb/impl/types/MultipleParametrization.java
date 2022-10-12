package org.utbot.jcdb.impl.types;

import java.util.ArrayList;
import java.util.List;

public class MultipleParametrization {

    public static class SuperTest1<T, W extends List<T>> {
    }

    public static class SuperTest2<T, Z extends List<T>, W extends List<Z>> {
        public W state;
    }

    public static class SuperTest3<Z extends ArrayList<String>, W extends List<Z>> extends SuperTest2<String, Z, W> {
    }
}
