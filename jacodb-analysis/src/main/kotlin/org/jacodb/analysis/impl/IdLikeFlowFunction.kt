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

package org.jacodb.analysis.impl

/**
 * Flow function which is equal to id for all elements from [domain] except those in [nonId], for which the result is stored in the map
 */
class IdLikeFlowFunction<D>(
    private val domain: Set<D>,
    private val nonId: Map<D, Collection<D>>
): FlowFunctionInstance<D> {
    override fun compute(fact: D): Collection<D> {
        nonId[fact]?.let {
            return it
        }
        return if (domain.contains(fact)) listOf(fact) else emptyList()
    }
}