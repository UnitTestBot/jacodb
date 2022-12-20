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

package org.utbot.jacodb.impl.types;

import java.io.Closeable;
import java.util.List;

public class InnerClasses<W> {

    public class InnerState {
        private W stateW;

        private <W extends List<Closeable>> W method() {
            return null;
        }
    }

    public class InnerStateOverriden<W> {
        private W stateW;

        private <W extends List<Integer>> W method() {
            return null;
        }
    }

    public <T> Runnable run(T with) {
        return new Runnable() {

            private T stateT;
            private W stateW;

            @Override
            public void run() {
                stateT = with;
            }
        };
    }

    public <T> InnerState newState(W with) {
        return new InnerState() {

            private T stateT;

        };
    }

    private InnerClasses<String> innerClasses;

    private InnerClasses<String>.InnerState stateString;
    private InnerClasses<String>.InnerStateOverriden<Closeable> stateClosable;

    public static InnerClasses<String> use(InnerClasses<String>.InnerState param) {
        return null;
    }

    public static InnerClasses<String> useOverriden(InnerClasses<String>.InnerStateOverriden<Closeable> param) {
        return null;
    }

    public InnerClasses<W> useW(InnerClasses<W> param) {
        return null;
    }
}
