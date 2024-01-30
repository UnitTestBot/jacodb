package org.jacodb.testing.analysis.alias.basic;

import org.jacodb.testing.analysis.alias.internal.TestUtil;

public class FieldSensitivity {
    public static class A {
        Object o;
    }

    private static void assign(A x, A y) {
        y.o = x.o;
    }

    public void testFieldSensitivity() {
        Object o = TestUtil.alloc(1);
        A x = new A();
        x.o = o;
        A y = new A();
        assign(x, y);
        TestUtil.check(
            "y.o",
            new String[]{"y.o", "o", "x.o"},
            new String[]{"x", "y"},
            new int[]{1}
        );
    }
}
