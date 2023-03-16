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

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ALL")
public class NPEExamples {

    static class SimpleClassWithField {
        public String field;
        SimpleClassWithField(String value) {
            this.field = value;
        }

        public String add566() {
            if (field != null) {
                return field + "566";
            }
            return null;
        }
    }

    interface SomeI {
        int functionThatCanThrowNPEOnNull(String x);
        int functionThatCanNotThrowNPEOnNull(String x);
    }

    static class SomeImpl implements SomeI {
        public int functionThatCanThrowNPEOnNull(String x) {
            return 0;
        }
        public int functionThatCanNotThrowNPEOnNull(String x) {
            return 0;
        }
    }

    static class AnotherImpl implements SomeI {
        public int functionThatCanThrowNPEOnNull(String x) {
            return x.length();
        }
        public int functionThatCanNotThrowNPEOnNull(String x) {
            return 0;
        }
    }

    private String constNull(String y) {
        return null;
    }

    private String id(String x) {
        return x;
    }

    private String twoExits(String x) {
        if (x != null && x.startsWith("239"))
            return x;
        return null;
    }

    public int npeOnLength() {
        String x = "abc";
        String y = "def";
        x = constNull(y);
        return x.length();
    }

    public int noNPE() {
        String x = null;
        String y = "def";
        x = id(y);
        return x.length();
    }

    public int npeAfterTwoExits() {
        String x = null;
        String y = "abc";
        x = twoExits(x);
        y = twoExits(y);
        return x.length() + y.length();
    }

    public int checkedAccess(String x) {
        if (x != null) {
            return x.length();
        }
        return -1;
    }

    public int consecutiveNPEs(String x, boolean flag) {
        int a = 0;
        int b = 0;
        if (flag) {
            a = x.length();
            b = x.length();
        }
        int c = x.length();
        return a + b + c;
    }

    int possibleNPEOnVirtualCall(@NotNull SomeI x, String y) {
        return x.functionThatCanThrowNPEOnNull(y);
    }

    int noNPEOnVirtualCall(@NotNull SomeI x, String y) {
        return x.functionThatCanNotThrowNPEOnNull(y);
    }

    int simpleNPEOnField() {
        SimpleClassWithField instance = new SimpleClassWithField("abc");
        String first = instance.add566();
        int len1 = first.length();
        instance.field = null;
        String second = instance.add566();
        int len2 = second.length();
        return len1 + len2;
    }
}