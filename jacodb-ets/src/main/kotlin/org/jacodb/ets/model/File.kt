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

package org.jacodb.ets.model

class EtsFile(
    val signature: EtsFileSignature,
    val classes: List<EtsClass>,
    val namespaces: List<EtsNamespace> = emptyList(),
    // TODO: importInfos: List<EtsImportInfo> = emptyList(),
    // TODO: exportInfos: List<EtsExportInfo> = emptyList(),
) {
    init {
        classes.forEach { (it as EtsClassImpl).declaringFile = this }
        namespaces.forEach { it.declaringFile = this }
    }

    var scene: EtsScene? = null

    val name: String
        get() = signature.fileName
    val projectName: String
        get() = signature.projectName

    val allClasses: List<EtsClass> by lazy {
        classes + namespaces.flatMap { it.allClasses }
    }

    override fun toString(): String {
        return signature.toString()
    }
}
