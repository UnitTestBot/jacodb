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

package org.jacodb.ets.test

import greeter.GreeterClient
import greeter.HelloRequest
import mu.KotlinLogging
import org.jacodb.ets.grpc.Server
import org.jacodb.ets.grpc.loadScene
import org.jacodb.ets.grpc.startArkAnalyzerServer
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.proto.toEts
import org.jacodb.ets.proto.toProto
import org.jacodb.ets.service.createGrpcClient
import org.jacodb.ets.test.utils.assumeNotNull
import org.jacodb.ets.test.utils.testFactory
import org.jacodb.ets.utils.getResourcePath
import org.jacodb.ets.utils.getResourcePathOrNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

class GrpcTest {
    companion object {
        private const val PORT = 50100

        private lateinit var server: Server

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            logger.info { "Setting up test environment..." }
            server = startArkAnalyzerServer(PORT)
            logger.info { "Done setting up test environment" }
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            logger.info { "Shutting down test environment..." }
            server.stop()
            logger.info { "Test environment shut down" }
        }

        fun getScene(path: Path): EtsScene {
            val scene = loadScene(PORT, path)
            logger.info { "Converting Scene from ProtoBuf to ETS..." }
            val (etsScene, timeConvert) = measureTimedValue {
                scene.toEts()
            }
            logger.info {
                "Done converting Scene in %.1fs"
                    .format(timeConvert.toDouble(DurationUnit.SECONDS))
            }
            return etsScene
        }
    }

    @Test
    fun `test Greeter`() {
        val greeter = createGrpcClient<GreeterClient>(PORT)
        val name = "Kotlin"
        val request = HelloRequest(name = name)
        logger.info { "Sending $request" }
        val response = greeter.SayHello().executeBlocking(request)
        logger.info { "Received $response" }
        assertTrue(response.message.isNotEmpty()) { "Response message should not be empty" }
        assertTrue(response.message.contains(name)) { "Response message should contain the name '$name'" }
    }

    @Test
    fun `load example`() {
        val res = "/samples/source/example.ts"
        val path = getResourcePath(res)
        val scene = getScene(path)
        assertTrue(scene.projectClasses.isNotEmpty())
    }

    @Test
    fun `load project`() {
        val res = "/projects/Photos/source"
        val path = getResourcePathOrNull(res)
        assumeNotNull(path) { "Project not available: $res" }
        val scene = getScene(path)
        assertTrue(scene.projectClasses.isNotEmpty())
    }

    @TestFactory
    fun `load all available project`() = testFactory {
        val prefix = "/projects"
        val base = getResourcePathOrNull(prefix) ?: run {
            logger.warn { "No projects directory found in resources" }
            return@testFactory
        }
        val availableProjects = base
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .map { it.name }
            .sorted()
        logger.info {
            buildString {
                appendLine("Found ${availableProjects.size} projects")
                for (path in availableProjects) {
                    appendLine("  - $path")
                }
            }
        }
        if (availableProjects.isEmpty()) {
            logger.warn { "No projects found" }
            return@testFactory
        }
        // container("load ${availableProjects.size} projects") {
        for (projectName in availableProjects) {
            test("load $projectName") {
                val timeStart = System.currentTimeMillis()

                val p = getResourcePath("$prefix/$projectName/source")
                val scene = getScene(p)
                assertTrue { scene.projectClasses.isNotEmpty() }

                logger.info { "Converting loaded scene to ProtoBuf..." }
                val proto = scene.toProto()
                assertTrue { proto.files.isNotEmpty() }

                logger.info {
                    "Done processing project $projectName in %.1fs"
                        .format((System.currentTimeMillis() - timeStart) / 1000.0)
                }
            }
        }
    }
}
