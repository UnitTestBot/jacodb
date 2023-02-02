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

package org.jacodb.testing.usages.fields;

public class FieldA {

    public int a;
    public int b;
    private final FieldB fieldB = new FieldB(a);

    public FieldA(int a, int b) {
        this.a = a;
        this.b = b;
    }

    public Boolean isPositive() {
        System.out.println(b);
        return a >= 0;
    }

    public void useA(int a) {
        System.out.println(b);
        this.a = a;
    }

    private void useCPrivate(int c) {
        System.out.println(a);
        fieldB.c = c;
    }

}


class FieldAImpl extends FieldA {

    public final FieldB fieldB = new FieldB(1);

    public FieldAImpl() {
        super(1, 1);
    }

    void hello() {
        System.out.println(a);
    }

    void fieldB() {
        System.out.println(fieldB);
    }
}


