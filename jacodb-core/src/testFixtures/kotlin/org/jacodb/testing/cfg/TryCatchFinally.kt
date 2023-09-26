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

package org.jacodb.testing.cfg

class TryCatchFinally {

    fun f(): Int {
        try {
            return 0
        } finally {
            try { // culprit ?? remove this try-catch and it works.
            } catch (ignore: Exception) {
            }
        }
    }

    fun box(): String {
        if (f() != 0) return "fail1"

        return "OK"
    }

}

class TryCatchFinally2 {
    fun test1(): String {
        var s = "";
        try {
            try {
                s += "Try";
                throw Exception()
            } catch (x: Exception) {
                s += "Catch";
                throw x
            } finally {
                s += "Finally";
            }
        } catch (x: Exception) {
            return s
        }
    }

    fun test2(): String {
        var s = "";

        try {
            s += "Try";
            throw Exception()
        } catch (x: Exception) {
            s += "Catch";
        } finally {
            s += "Finally";
        }

        return s
    }


    fun box(): String {
        if (test1() != "TryCatchFinally") return "fail1: ${test1()}"

        if (test2() != "TryCatchFinally") return "fail2: ${test2()}"

        return "OK"
    }

}

class TryCatchFinally3 {
    fun unsupportedEx() {
        if (true) throw UnsupportedOperationException()
    }

    fun runtimeEx() {
        if (true) throw RuntimeException()
    }

    fun test1WithFinally(): String {
        var s = "";
        try {
            try {
                s += "Try";
                unsupportedEx()
            } finally {
                s += "Finally"
            }
        } catch (x: RuntimeException) {
            return s
        }
        return s + "Failed"
    }


    fun test2WithFinally(): String {
        var s = "";
        try {
            try {
                s += "Try";
                unsupportedEx()
                return s
            } finally {
                s += "Finally"
            }
        } catch (x: RuntimeException) {
            return s
        }
    }

    fun box(): String {
        if (test1WithFinally() != "TryFinally") return "fail2: ${test1WithFinally()}"

        if (test2WithFinally() != "TryFinally") return "fail4: ${test2WithFinally()}"
        return "OK"
    }
}

class TryCatchFinally4{
    fun unsupportedEx() {
        if (true) throw UnsupportedOperationException()
    }

    fun runtimeEx() {
        if (true) throw RuntimeException()
    }

    fun test1() : String {
        var s = "";
        try {
            try {
                s += "Try";
                unsupportedEx()
            } catch (x : UnsupportedOperationException) {
                s += "Catch";
                runtimeEx()
            } catch (e: RuntimeException) {
                s += "WrongCatch"
            }
        } catch (x : RuntimeException) {
            return s
        }
        return s + "Failed"
    }

    fun test1WithFinally() : String {
        var s = "";
        try {
            try {
                s += "Try";
                unsupportedEx()
            } catch (x : UnsupportedOperationException) {
                s += "Catch";
                runtimeEx()
            } catch (e: RuntimeException) {
                s += "WrongCatch"
            } finally {
                s += "Finally"
            }
        } catch (x : RuntimeException) {
            return s
        }
        return s + "Failed"
    }

    fun test2() : String {
        var s = "";
        try {
            try {
                s += "Try";
                unsupportedEx()
                return s
            } catch (x : UnsupportedOperationException) {
                s += "Catch";
                runtimeEx()
                return s
            } catch (e: RuntimeException) {
                s += "WrongCatch"
            }
        } catch (x : RuntimeException) {
            return s
        }
        return s + "Failed"
    }

    fun test2WithFinally() : String {
        var s = "";
        try {
            try {
                s += "Try";
                unsupportedEx()
                return s
            } catch (x : UnsupportedOperationException) {
                s += "Catch";
                runtimeEx()
                return s
            } catch (e: RuntimeException) {
                s += "WrongCatch"
            } finally {
                s += "Finally"
            }
        } catch (x : RuntimeException) {
            return s
        }
        return s + "Failed"
    }



    fun box() : String {
        if (test1() != "TryCatch") return "fail1: ${test1()}"
        if (test1WithFinally() != "TryCatchFinally") return "fail2: ${test1WithFinally()}"

        if (test2() != "TryCatch") return "fail3: ${test2()}"
        if (test2WithFinally() != "TryCatchFinally") return "fail4: ${test2WithFinally()}"
        return "OK"
    }

}