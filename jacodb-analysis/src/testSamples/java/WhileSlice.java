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

public class WhileSlice {
    public static void main(String[] args) {
        ExampleWithField x = new ExampleWithField();
        ExampleWithField y = new ExampleWithField();
        ExampleWithField z = new ExampleWithField();
        f1(x);
        f2(y);
        f3(z);
        while (x.f > 0) {
            System.out.println(x);
            x = y;
            z = x;
        }
    }
    public static void f1(ExampleWithField a) {
        a.f = 10;
    }

    public static void f2(ExampleWithField b) {
        b.f = 20;
    }

    public static void f3(ExampleWithField c) {
        c.f = 30;
    }
}
