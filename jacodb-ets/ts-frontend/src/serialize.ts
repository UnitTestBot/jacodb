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
import { LexicalEnvTypeDefinitionDto, LexicalEnvTypeRefDto } from "./dto/types";

/**
 * Serialize an EtsFileDto to JSON consumable by Kotlin `EtsFileDto.loadFromJson`.
 *
 * `JSON.stringify` drops `undefined`-valued fields, which is exactly what we want:
 * optional DTO fields correspond to Kotlin constructor parameters with defaults.
 * `null` values (e.g. `superClassName: null`, `GlobalRef.ref: null`) are preserved.
 *
 * Lexical environments form a shared graph: nested closures capture enclosing
 * environments and function signatures. Inline JSON duplicates that graph at
 * every use, potentially exceeding the runtime's string limit. File-local
 * references preserve every method and capture while serializing each complete
 * environment once. Definition rows are concrete, like `body.locals`, so only
 * use-site references carry a polymorphic discriminator.
 */
export function serializeEtsFile(file: EtsFileDto): string {
    const environments = [...(file.lexicalEnvTypes ?? [])];
    const environmentIds = new Map<object, number>();
    const rewritten = new WeakMap<object, object>();

    // Preserve an existing table's indices when a compact DTO is serialized again.
    environments.forEach((environment, index) => environmentIds.set(environment, index));

    const rewriteFields = (value: object): object => {
        const result: Record<string, unknown> | unknown[] = Array.isArray(value) ? new Array(value.length) : {};
        rewritten.set(value, result);
        for (const [key, child] of Object.entries(value)) {
            (result as Record<string, unknown>)[key] = rewrite(child);
        }
        return result;
    };

    const rewrite = (value: unknown): unknown => {
        if (value === null || typeof value !== "object") return value;

        if ((value as { _?: string })._ === "LexicalEnvType") {
            let id = environmentIds.get(value);
            if (id === undefined) {
                id = environments.length;
                environmentIds.set(value, id);
                environments.push(value as LexicalEnvTypeDefinitionDto);
                const definition = rewriteFields(value) as LexicalEnvTypeDefinitionDto & { _?: string };
                delete definition._;
                environments[id] = definition;
            }
            const reference: LexicalEnvTypeRefDto = { _: "LexicalEnvTypeRef", id };
            return reference;
        }

        const existing = rewritten.get(value);
        return existing ?? rewriteFields(value);
    };

    const originalEnvironmentCount = environments.length;
    for (let index = 0; index < originalEnvironmentCount; index++) {
        environments[index] = rewriteFields(environments[index]) as LexicalEnvTypeDefinitionDto;
    }
    const source = { ...file };
    delete source.lexicalEnvTypes;
    const compact = rewrite(source) as EtsFileDto;
    if (environments.length > 0) compact.lexicalEnvTypes = environments;

    return JSON.stringify(compact);
}
