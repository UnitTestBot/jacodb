/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import { EtsFileDto } from "./dto/model";

/**
 * Serialize an EtsFileDto to JSON consumable by Kotlin `EtsFileDto.loadFromJson`.
 *
 * `JSON.stringify` drops `undefined`-valued fields, which is exactly what we want:
 * optional DTO fields correspond to Kotlin constructor parameters with defaults.
 * `null` values (e.g. `superClassName: null`, `GlobalRef.ref: null`) are preserved.
 */
export function serializeEtsFile(file: EtsFileDto, pretty: boolean = false): string {
    return pretty ? JSON.stringify(file, null, 2) : JSON.stringify(file);
}
