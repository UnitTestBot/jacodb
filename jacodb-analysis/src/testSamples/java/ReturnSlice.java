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

public class ReturnSlice {
    public static void main(String[] args) {
        int x = 0;
        int z = 10;
        x = f(z);
        System.out.println(x); // sink
    }

    public static int f(int x) {
        int y = x + 1;
        int z = y + 1;
        if (x > 0) {
            return y;
        }
        return z;
    }
}
