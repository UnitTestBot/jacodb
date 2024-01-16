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

package org.jacodb.api.jvm.cfg

interface Graph<NODE> : Iterable<NODE> {

    fun successors(node: NODE): Set<NODE>
    fun predecessors(node: NODE): Set<NODE>
}

interface JcBytecodeGraph<NODE> : Graph<NODE> {

    val entries: List<NODE>
    val exits: List<NODE>

    fun throwers(node: NODE): Set<NODE>
    fun catchers(node: NODE): Set<NODE>

}