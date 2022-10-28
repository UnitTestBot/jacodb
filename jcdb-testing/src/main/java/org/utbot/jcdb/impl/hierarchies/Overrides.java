package org.utbot.jcdb.impl.hierarchies;

import java.util.List;

public class Overrides {

    public interface Iface<T> {

        public T runMain(T in);
    }

    public static class Main<T> {

        public T runMain(List<T> in) {
            return null;
        }

        public T runMain(T in) {
            return null;
        }
    }

    public static class Impl1 extends Main<String> {

        @Override
        public String runMain(List<String> in) {
            return super.runMain(in);
        }

        @Override
        public String runMain(String in) {
            return super.runMain(in);
        }
    }

    public static class Impl2 extends Impl1 implements Iface<String> {
        @Override
        public String runMain(List<String> in) {
            return super.runMain(in);
        }

        @Override
        public String runMain(String in) {
            return super.runMain(in);
        }
    }
}
