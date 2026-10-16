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

package org.jacodb.testing.lookup;

public class SpecialMethodFixtures {
    public interface DefaultValue {
        default int value() {
            return 1;
        }

        static int staticValue() {
            return 2;
        }
    }

    public interface MoreSpecificValue extends DefaultValue {
        @Override
        default int value() {
            return 3;
        }
    }

    public interface AbstractValue extends DefaultValue {
        @Override
        int value();
    }

    public static class DefaultBase implements DefaultValue {
    }

    public static class DefaultChild extends DefaultBase {
        public int callSuper() {
            return super.value();
        }
    }

    public static class ClassValue {
        public int value() {
            return 4;
        }
    }

    public static class ClassValueBase extends ClassValue {
    }

    public static class ClassValueChild extends ClassValueBase implements DefaultValue {
    }

    public static class SpecificValue implements DefaultValue, MoreSpecificValue {
    }

    public abstract static class AbstractUser implements AbstractValue {
    }

    public interface GenericValue<T> {
        default T get() {
            return null;
        }
    }

    public static class GenericBase implements GenericValue<String> {
    }

    public static class GenericChild extends GenericBase {
        public String callSuper() {
            return super.get();
        }
    }

    public static class ConstructorBase {
        public ConstructorBase(int value) {
        }
    }

    public static class ConstructorChild extends ConstructorBase {
        public ConstructorChild() {
            super(1);
        }

        public static int staticValue() {
            return 5;
        }

        private int privateValue() {
            return 6;
        }
    }
}
