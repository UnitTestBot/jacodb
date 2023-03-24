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

    static class ContainerOfSimpleClass {
        public SimpleClassWithField g;
        ContainerOfSimpleClass(SimpleClassWithField inner) {
            this.g = inner;
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

    static class RecursiveClass {
        RecursiveClass rec = null;
        RecursiveClass(RecursiveClass other) {
            rec = other;
        }
        RecursiveClass() {rec = null;};
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

    private int taintIt(String in, SimpleClassWithField out) {
        SimpleClassWithField x = new SimpleClassWithField("abc"); // Needed because otherwise cfg will optimize aliasing out
        x = out;
        x.field = in;
        return out.field.length();
    }

    private void foo(ContainerOfSimpleClass z) {
        SimpleClassWithField x = z.g;
        x.field = null;
    }

    int npeOnLength() {
        String x = "abc";
        String y = "def";
        x = constNull(y);
        return x.length();
    }

    int noNPE() {
        String x = null;
        String y = "def";
        x = id(y);
        return x.length();
    }

    int npeAfterTwoExits() {
        String x = null;
        String y = "abc";
        x = twoExits(x);
        y = twoExits(y);
        return x.length() + y.length();
    }

    int checkedAccess(String x) {
        if (x != null) {
            return x.length();
        }
        return -1;
    }

    int consecutiveNPEs(String x, boolean flag) {
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

    int simplePoints2() {
        SimpleClassWithField a = new SimpleClassWithField("abc");
        SimpleClassWithField b = new SimpleClassWithField("kek"); // We can't directly set b=a, or cfg will optimize this and use one variable
        b = a;
        b.field = null;
        return a.field.length();
    }

    // Test from far+14, figure 2
    int complexAliasing() {
        ContainerOfSimpleClass a = new ContainerOfSimpleClass(new SimpleClassWithField("abc"));
        SimpleClassWithField b = a.g;
        foo(a);
        return b.field.length();
    }

    // Test from far+14, Listing 2
    int contextInjection() {
        SimpleClassWithField p = new SimpleClassWithField("abc");
        SimpleClassWithField p2 = new SimpleClassWithField("def");
        taintIt(null, p);
        int a = p.field.length();
        taintIt("normal", p2);
        int b = p2.field.length();
        return a + b;
    }

    // Test from far+14, Listing 3
    int flowSensitive() {
        SimpleClassWithField p = new SimpleClassWithField("abc");
        SimpleClassWithField p2 = new SimpleClassWithField("def");
        p2 = p;
        int a = p2.field.length();
        p.field = null;
        int b = p2.field.length();
        return a + b;
    }

    int overriddenNullInCallee() {
        // Here call to constructor for instance firstly sets instance.rec = null, then sets instance.rec = arg$0
        // Fact from instance.rec = null shouldn't go to instruction with toString() after backward analysis spawned by the latter
        RecursiveClass instance = new RecursiveClass(new RecursiveClass());
        instance.rec.toString();
        return 0;
    }

    int recursiveClass() {
        RecursiveClass instance = new RecursiveClass(new RecursiveClass(new RecursiveClass()));
        instance.rec.rec.toString(); // no NPE
        instance.rec.rec.rec.toString(); // NPE
        instance.rec = instance.rec.rec;
        instance.rec.rec.toString(); // NPE
        instance.rec.toString(); // no NPE
        while (instance.hashCode() > 0) {
            instance.rec = new RecursiveClass(); // creating possibly infinite chain of RecursiveClasses
            instance = instance.rec;
        }
        instance.toString(); // no NPE
        return 0;
    }
}