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

package org.jacodb.testing.cfg;

/*
 * @testcase SimpleAlias1
 *
 * @version 1.0
 *
 * @author Johannes Späth, Nguyen Quang Do Lisa (Secure Software Engineering Group, Fraunhofer
 * Institute SIT)
 *
 * @description Direct alias
 */
public class SimpleAlias1 {

    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a = (A) Benchmark.taint(); //new A();
        A b = new A(); // Added to avoid cfg optimizations
        Benchmark.use(b);
        b = a;
        Benchmark.use(b);
        Benchmark.use(a);
        Benchmark.test("b",
                "{allocId:1, mayAlias:[a,b], notMayAlias:[], mustAlias:[a,b], notMustAlias:[]}");
    }
}

class A {

    // Object A with attributes of type B

    public int i = 5;


}


class Benchmark {

    public static void alloc(int id) {

    }

    public static void test(String targetVariable, String results) {

    }

    public static void use(Object o) {
        o.hashCode();
        //A method to be used to avoid the compiler to prune the Object
    }

    public static Object taint() {
        return new Object();
    }
}

class RealMethodResolution {
    interface Virtual {
        void action(Object any);
    }

    static class VirtualImpl implements Virtual {
        public void action(Object any) {
            System.out.println(any);
        }
    }

    public void test() {
        Virtual v = new VirtualImpl();
        v.action(new Object());
    }
}
