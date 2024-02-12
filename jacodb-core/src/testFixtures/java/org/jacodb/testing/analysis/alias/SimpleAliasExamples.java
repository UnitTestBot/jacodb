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

package org.jacodb.testing.analysis.alias;

public class SimpleAliasExamples {
    public static class Holder {
        public Object o;
    }
    public static class OuterHolder {
        public Holder h;
    }
    public void overFields() {
        Object o = new Object();
        Holder h1 = new Holder();
        h1.o = o;
        Holder h2 = new Holder();
        h2.o = o;
        OuterHolder oh = new OuterHolder();
        oh.h = h2;
    }

    public void overSeveralFields() {
        Object o = new Object();
        Holder h1 = new Holder();
        h1.o = o;
        Holder h2 = new Holder();
        h2.o = o;
        OuterHolder oh = new OuterHolder();
        oh.h = h2;
        Holder h4 = new Holder();
        OuterHolder oh2 = new OuterHolder();
        oh2.h = h4;
        oh2.h.o = oh.h.o;
    }

    public void overFunctionParameters() {
        Object o = new Object();
        Holder h1 = new Holder();
        h1.o = o;

        Holder h2 = new Holder();
        Holder h3 = new Holder();
        copy(h1, h2, h3);

        Holder h4 = new Holder();
        Holder h5 = new Holder();
        copy(h2, h4, h5);

        Holder h6 = new Holder();
        Holder h7 = new Holder();
        copy(h5, h6, h7);
    }

    private void copy(Holder from, Holder to1, Holder to2) {
        to1.o = from.o;
        to2.o = from.o;
    }

    public void overFunctionReturn() {
        Object o = new Object();
        Holder h1 = new Holder();
        h1.o = o;
        Holder h2 = copy(h1);
        Holder h3 = copy(h2);
    }

    private Holder copy(Holder from) {
        Holder h = new Holder();
        h.o = from.o;
        return h;
    }

    public void overFieldsSmall() {
        Object o = new Object();
        Holder h = new Holder();
        Holder h2 = new Holder();
        h2 = h;
        h2.o = new Object();
        h.o = o;
    }
}
