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

package org.utbot.jcdb.impl.usages;

import java.util.Collection;
import java.util.List;

public class WithInner {

    public class Inner {
        public void hello() {
            System.out.println("hello");
        }
    }

    public static class StaticInner {
        public void helloStatic() {
            System.out.println("hello static");
        }
    }

    public void sayHello() {
        new Inner().hello();
        new StaticInner().helloStatic();
        new Runnable() {
            @Override
            public void run() {
                System.out.println("hello anonymous");
            }
        }.run();

    }

    public static void main(String[] args) {
        new WithInner1<List<String>>();
    }
}


class WithInner1<T extends Collection<?>> {

}
