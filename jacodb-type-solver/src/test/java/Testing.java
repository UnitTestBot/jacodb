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

import java.util.Set;

public class Testing<T1, T2 extends Set<Integer>> extends Ancestor<T1, Set<T2>> implements ParametrizedFirst<T1, Ancestor<T1, T2>>, ParametrizedSecond<ParametrizedFirst<T1, String>, T1> {
}

interface ParametrizedFirst<F1, F2> {}
interface ParametrizedSecond<S1, S2> {}

class Ancestor<A1, A2> {}

class RecursiveGeneric<T extends RecursiveGeneric> {}
