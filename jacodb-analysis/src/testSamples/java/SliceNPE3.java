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

import java.util.ArrayList;
import java.util.List;

public class SliceNPE3 {
    public static void main(String[] args) {
        List<Integer> counter = new ArrayList<>();
        counter.add(1);
        List<Integer> one = new ArrayList<>();
        one.add(1);
        List<Integer> npe = null;
        if (counter.get(0) > one.get(0)) {
            npe = new ArrayList<>();
        }
        while (counter.get(0) < 11) {
            f(counter, npe);
        }
        System.out.println(counter);
        System.out.println(npe.get(0));
    }
    public static void f(List<Integer> x, List<Integer> y) {
        f1(x, y);
        f2(y);
    }

    public static void f1(List<Integer> a, List<Integer> b) {
        a.set(0, a.get(0) + 1);
        System.out.println(b);
    }

    public static void f2(List<Integer> z) {
        if (z == null) {
            z = new ArrayList<>();
        }
        List<Integer> one = new ArrayList<>();
        one.add(1);
        f1(one, z);
    }
}
