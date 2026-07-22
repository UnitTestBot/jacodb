/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";

const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const typescriptLibDir = path.join(frontendDir, "node_modules", "typescript", "lib");
const distDir = path.join(frontendDir, "dist");

fs.mkdirSync(distDir, { recursive: true });
for (const entry of fs.readdirSync(distDir, { withFileTypes: true })) {
    if (entry.isFile() && /^lib(?:\..+)?\.d\.ts$/.test(entry.name)) {
        fs.unlinkSync(path.join(distDir, entry.name));
    }
}
for (const entry of fs.readdirSync(typescriptLibDir, { withFileTypes: true })) {
    if (entry.isFile() && /^lib(?:\..+)?\.d\.ts$/.test(entry.name)) {
        fs.copyFileSync(path.join(typescriptLibDir, entry.name), path.join(distDir, entry.name));
    }
}
