package org.utbot.jcdb.impl.types;

import java.util.ArrayList;
import java.util.List;

public class MultipleParametrization {

    public static class SuperTest1<T, W extends List<T>> {
        T stateT;
        W stateW;

        T runT(T in) {
            return null;
        }

        W runW(W in) {
            return null;
        }
    }

    public static class SuperTest2<W extends List<String>> extends SuperTest1<String, W> {
    }

    public static class SuperTest3 extends SuperTest2<ArrayList<String>> {
    }

}
