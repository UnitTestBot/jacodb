/**
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
package org.utbot.jcdb.impl.hierarchies;

import java.io.Closeable;
import java.util.List;

public class Overrides {

    public interface Iface<T> {

        public T runMain(T in);

        default public T runMain(Closeable in) {
            return null;
        }
    }

    public static class Main<T> {

        private T main;
        protected T protectedMain;
        public T publicMain;

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
        private String main;
        protected List<Closeable> protectedMain1;
        public List<Closeable> publicMain1;

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
