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

import java.util.function.Function;

public class InvokeDynamicExamples {

    private static int runUnaryFunction(String data, Function<String, Integer> f) {
        if (data.isEmpty()) {
            return -1;
        }

        int result = f.apply(data);
        return result + 17;
    }

    private static int runSamFunction(String data, SamBase f) {
        if (data.isEmpty()) {
            return -1;
        }
        int result = f.samFunction(data);
        return result + 17;
    }

    private static int runDefaultFunction(String data, SamBase f) {
        if (data.isEmpty()) {
            return -1;
        }
        int result = f.defaultFunction(data);
        return result + 17;
    }

    private static String runComplexStringConcat(String str, int v) {
        return str + v + 'x' + str + 17 + str;
    }

    public interface SamBase {
        int samFunction(String data);

        default int defaultFunction(String data) {
            if (data.isEmpty()) {
                return -2;
            }
            return samFunction(data) + 31;
        }
    }

    private static int add(int a, int b) {
        return a + b;
    }

    public static String testUnaryFunction() {
        int res = runUnaryFunction("abc", s -> s.length());
        return res == ("abc".length() + 17) ? "OK" : "BAD";
    }

    public static String testMethodRefUnaryFunction() {
        int res = runUnaryFunction("abc", String::length);
        return res == ("abc".length() + 17) ? "OK" : "BAD";
    }

    public static String testCurryingFunction() {
        Function<Integer, Integer> add42 = x -> add(x, 42);
        int res = runUnaryFunction("abc", s -> add42.apply(s.length()));
        return res == ("abc".length() + 17 + 42) ? "OK" : "BAD";
    }

    public static String testSamFunction() {
        int res = runSamFunction("abc", s -> s.length());
        return res == ("abc".length() + 17) ? "OK" : "BAD";
    }

    public static String testSamWithDefaultFunction() {
        int res = runDefaultFunction("abc", s -> s.length());
        return res == ("abc".length() + 17 + 31) ? "OK" : "BAD";
    }

    public static String testComplexInvokeDynamic() {
        String expected = "abc42xabc17abc";
        String actual = runComplexStringConcat("abc", 42);
        return expected.equals(actual) ? "OK" : "BAD";
    }
}
