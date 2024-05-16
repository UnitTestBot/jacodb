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

package org.jacodb.analysis.ifds.domain

sealed interface CallAction<Fact> {
    /**
     * Call-to-return-site action.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   :
     *   : (call-to-return-site edge)
     *   :
     * [ RETURN FROM p ] :: returnSite
     * ```
     */
    data class Return<Fact>(
        val fact: Fact
    ) : CallAction<Fact>

    /**
     * Call-to-start action.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   : \
     *   :  \ (call-to-start edge)
     *   :   \
     *   :  [ START p ] :: calleeStart
     *   :    |
     *   :  [ EXIT p ]
     *   :   /
     *   :  /
     * [ RETURN FROM p ]
     * ```
     */
    data class Start<Fact>(
        val fact: Fact
    ) : CallAction<Fact>
}