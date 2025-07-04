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

package org.jacodb.ets.grpc

import manager.GetSceneRequest
import manager.ManagerClient
import mu.KotlinLogging
import org.jacodb.ets.service.createGrpcClient
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue
import model.Scene as ProtoScene

private val logger = KotlinLogging.logger {}

fun loadScene(
    port: Int,
    path: Path,
    inferTypes: Boolean = false,
): ProtoScene {
    logger.info { "Connecting to gRPC server on port $port..." }
    val manager = createGrpcClient<ManagerClient>(port)
    logger.info { "Requesting Scene for '$path'..." }
    val (scene, timeLoad) = measureTimedValue {
        val request = GetSceneRequest(
            path = path.pathString,
            infer_types = inferTypes,
        )
        manager.GetScene().executeBlocking(request)
    }
    logger.info {
        "Received Scene in %.1fs with ${
            scene.files.size
        } files, ${
            scene.files.sumOf { f -> f.classes.size }
        } classes, ${
            scene.files.sumOf { f -> f.classes.sumOf { cls -> cls.methods.size } }
        } methods"
            .format(timeLoad.toDouble(DurationUnit.SECONDS))
    }
    return scene
}
