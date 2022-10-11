package org.utbot.jcdb.impl.types;

import java.util.List;

public class ClassWithInnersLinkedToMethod2<W> {

    public class State<T> {
        public T stateT;
        public List<T> stateW;
    }

    public class Impl extends ClassWithInnersLinkedToMethod2<String> {
    }

    public State<W> run() {
        return new State<W>() {
        };
    }

}
