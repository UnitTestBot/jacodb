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

package org.utbot.jcdb.impl.types;

import java.util.ArrayList;
import java.util.List;

public class MultipleParametrization {

    public static class SuperTest1<T, W extends List<T>, Z extends List<W>> {
        T stateT;
        W stateW;
        Z stateZ;

        T runT(T in) {
            return null;
        }

        W runW(W in) {
            return null;
        }

        Z runZ(Z in) {
            return null;
        }
    }

    public static class SuperTest2<W extends List<String>, Z extends List<W>> extends SuperTest1<String, W, Z> {
    }

    public static class SuperTest3 extends SuperTest2<ArrayList<String>, ArrayList<ArrayList<String>>> {
    }

}
