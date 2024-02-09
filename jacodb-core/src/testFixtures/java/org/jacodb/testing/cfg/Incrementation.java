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

public class Incrementation {

    static public int iinc(int x) {
        return x++;
    }

    static public int[] iincArrayIntIdx() {
        int[] arr = new int[3];
        int idx = 0;
        arr[idx++] = 1;
        arr[++idx] = 2;
        return arr;
    }

    static public int[] iincArrayByteIdx() {
        int[] arr = new int[3];
        byte idx = 0;
        arr[idx++] = 1;
        arr[++idx] = 2;
        return arr;
    }

    static public int[] iincFor() {
        int[] result = new int[5];
        for (int i = 0; i < 5; i++) {
            result[i] = i;
        }
        return result;
    }

    static public int[] iincIf(boolean x, boolean y) {
        int xx = 0;
        if (x != y) {
            xx++;
        }
        return new int[xx];
    }

    static public int iincWhile() {
        int x = 0;
        int y = 0;
        while (x++ < 2) {
            y++;
        }
        return y;
    }

    static public int iincIf2(int x) {
        if (x++ == 1) {
            return x;
        }
        return x + 1;
    }

    public static String iincCustomWhile() {
        int x = 0;

        while (x++ < 5) {
        }

        return x != 6 ? "Fail: " + x : "OK";
    }

}
