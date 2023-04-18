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

package org.jacodb.analysis.codegen.ast.base

interface Commentable {
    var comments: ArrayList<String>
    fun setCommentWithShift(numOfComment: Int, newComment: String) {
        while (comments.size < numOfComment + 1) {
            comments.add("")
        }
        if (numOfComment <= comments.size - 1) {
            comments.add("")
            for (i in comments.size - 2 downTo numOfComment) {
                comments.set(i + 1, comments.get(i))
            }
        }
        comments[numOfComment] = newComment
    }

    fun setComment(numOfComment: Int, newComment: String) {
        comments.set(numOfComment, newComment)
    }

    fun addComments(addingTarget: Commentable) {
        comments.addAll(addingTarget.comments)
    }

    fun addCommentsWithRemove(addingTarget: Commentable) {
        comments.addAll(addingTarget.comments)
        addingTarget.comments.clear()
    }

    fun addComments(commentsToAdd: List<String>) {
        comments.addAll(commentsToAdd)
    }
}