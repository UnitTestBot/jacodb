package org.utbot.jcdb.impl.types;

public class ClassWithInnersLinkedToMethod<W> {

    public <T> Runnable run(T with) {
        return new Runnable() {

            private T stateT;
            private W stateW;

            @Override
            public void run() {
                stateT = with;
            }
        };
    }
}
