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
package org.utbot.jcdb.impl.builders;

import java.util.List;

public class Interfaces {

    public static interface Interface {

    }

    public static class Impl1 implements Interface {
    }

    public static class Impl2 extends Impl1 {
    }



    public Interface build1() {
        return new Impl1();
    }

    public Interface build2(Impl2 impl2) {
        return impl2;
    }

    public Interface build3(List<Impl2> list) {
        return list.get(0);
    }
}
