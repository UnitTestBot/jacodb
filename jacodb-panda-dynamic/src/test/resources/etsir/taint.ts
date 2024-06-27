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

function source(): number | null {
    return null
}

function pass(data: number | null): number | null {
    return data
}

function validate(data: number | null): number {
    if (data == null) return 0
    return data
}

function sink(data: number | null) {
    if (data == null) throw new Error("Error!")
}

function bad() {
    let data = source()
    data = pass(data)
    sink(data)
}

function good() {
    let data = source()
    data = validate(data)
    sink(data)
}
