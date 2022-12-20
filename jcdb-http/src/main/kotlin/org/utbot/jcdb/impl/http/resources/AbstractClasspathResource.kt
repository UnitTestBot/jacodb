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

package org.utbot.jcdb.impl.http.resources

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.utbot.jcdb.api.FieldUsageMode
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.ext.findFieldOrNull
import org.utbot.jcdb.api.ext.findMethodOrNull
import org.utbot.jcdb.impl.features.hierarchyExt
import org.utbot.jcdb.impl.features.usagesExt
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

abstract class AbstractClasspathResource {

    private var job: Job? = null

    abstract fun intervalAction()

    @PostConstruct
    fun setup() {
        job = GlobalScope.launch {
            while (true) {
                delay(5_000)
                intervalAction()
            }
        }
    }

    @PreDestroy
    fun cleanup() {
        job?.cancel()
    }


    fun JcClasspath.findClass(className: String): ClassEntity {
        val jcClass = findClassOrNull(className) ?: throw NotFoundException("Class not found by $className")
        return jcClass.toEntity()
    }

    fun JcClasspath.classBytecode(className: String): HttpEntity<ByteArray> {
        val jcClass = findClassOrNull(className) ?: throw NotFoundException("Class not found by $className")
        val bytes = jcClass.binaryBytecode()

        return HttpEntity<ByteArray>(bytes, HttpHeaders().also {
            it.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${jcClass.simpleName}.class")
            it.contentLength = bytes.size.toLong()
        })
    }

    suspend fun JcClasspath.findSubclasses(
        optionalSkip: Int?,
        optionalTop: Int?,
        className: String,
        allHierarchy: Boolean?
    ): ClassRefPaginator {
        val all = allHierarchy ?: false
        val top = optionalTop ?: 50
        val skip = optionalSkip ?: 0
        return hierarchyExt().findSubClasses(className, all)
            .map { ClassRefEntity(it.name) }
            .toPaginator(skip, top)
    }

    suspend fun JcClasspath.findFieldUsages(
        optionalSkip: Int?,
        optionalTop: Int?,
        className: String,
        fieldName: String,
        mode: FieldUsageMode
    ): MethodRefPaginator {
        val top = optionalTop ?: 50
        val skip = optionalSkip ?: 0
        val ext = usagesExt()
        val jcClass = findClassOrNull(className)
            ?: throw NotFoundException("Class $className not found by name")
        val field = jcClass.findFieldOrNull(fieldName)
            ?: throw NotFoundException("Field $className#$fieldName not found")
        return ext.findUsages(field, mode)
            .map { MethodRefEntity(it.name, it.description, it.enclosingClass.declaredMethods.indexOf(it)) }
            .toPaginator(skip, top)
    }

    suspend fun JcClasspath.findMethodsUsages(
        optionalSkip: Int?,
        optionalTop: Int?,
        className: String,
        methodName: String,
        methodDescription: String?,
        methodOffset: Int?,
    ): MethodRefPaginator {
        val top = optionalTop ?: 50
        val skip = optionalSkip ?: 0
        val ext = usagesExt()
        val jcClass = findClassOrNull(className)
            ?: throw NotFoundException("Class $className not found by name")
        val method = when {
            methodDescription != null -> {
                jcClass.findMethodOrNull(methodName, methodDescription)
                throw BadRequestException("No method found by $methodName: $methodDescription in $className")
            }

            methodOffset != null -> jcClass.declaredMethods[methodOffset]
            else -> throw BadRequestException("`description` or `offset` should be specified")
        }
        return ext.findUsages(method)
            .map { MethodRefEntity(it.name, it.description, it.enclosingClass.declaredMethods.indexOf(it)) }
            .toPaginator(skip, top)
    }

}