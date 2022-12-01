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

public class Generics {

    static class LinkedBase<T, W extends List<T>> {
        T state;
        W stateW;
        List<W> stateListW;
    }


    static class LinkedImpl<W extends List<String>> extends LinkedBase<String, W> {
    }


    static class SingleBase<T> {

        private T state;
        private ArrayList<T> stateList;

        T run1(T incoming) {
            state = incoming;
            stateList.add(incoming);
            return incoming;
        }

        <W extends T> W run2(List<W> incoming) {
            state = incoming.get(0);
            stateList.addAll(incoming);
            return incoming.get(0);
        }

    }

    static class SingleImpl extends SingleBase<String> {}
}
