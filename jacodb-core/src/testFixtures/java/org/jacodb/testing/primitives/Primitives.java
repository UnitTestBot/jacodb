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

package org.jacodb.testing.primitives;

public class Primitives {

    public int example(short s, char c) {
        return s + c;
    }

    public int example(byte s, short c) {
        return s + c;
    }

    public long example(int s, long c) {
        return s + c;
    }

    public float example(int s, float c) {
        return s + c;
    }

    public int example(char s, char c) {
        return s + c;
    }

    public int unaryExample(char a) {
        return -a;
    }

    public int unaryExample(byte a) {
        return -a;
    }

    public int unaryExample(short a) {
        return -a;
    }

    public long unaryExample(long a) {
        return -a;
    }

    public float unaryExample(float a) {
        return -a;
    }
}
