package org.utbot.jcdb.impl.types;

import java.io.Closeable;
import java.util.List;

public class InnerClasses<W> {

    public class InnerState {
        private W stateW;

        private <W extends List<Closeable>> W method() {
            return null;
        }
    }

    public class InnerStateOverriden<W> {
        private W stateW;

        private <W extends List<Closeable>> W method() {
            return null;
        }
    }

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

    public <T> InnerState newState(W with) {
        return new InnerState() {

            private T stateT;

        };
    }

    private InnerClasses<String> innerClasses;

    private InnerState state;
    private InnerStateOverriden<Closeable> state2;

    public InnerClasses<String> use(InnerClasses<String> param) {
        return null;
    }

    public InnerClasses<W> useW(InnerClasses<W> param) {
        return null;
    }
}
