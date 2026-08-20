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

const fs = require("node:fs");
const path = require("node:path");

const frontendDir = path.resolve(__dirname, "..");
const typescriptLibDir = path.join(frontendDir, "node_modules", "typescript", "lib");
const distDir = path.join(frontendDir, "dist");

const LIB_FILE_PATTERN = /^lib(?:\..+)?\.d\.ts$/;

// Fail BEFORE deleting anything: otherwise a missing/renamed source directory
// would leave `dist` without any type libraries and still exit successfully.
if (!fs.existsSync(typescriptLibDir)) {
    throw new Error(`TypeScript lib directory not found: ${typescriptLibDir}. Did you run 'npm ci'?`);
}
const libFiles = fs
    .readdirSync(typescriptLibDir, { withFileTypes: true })
    .filter((entry) => entry.isFile() && LIB_FILE_PATTERN.test(entry.name))
    .map((entry) => entry.name);
if (libFiles.length === 0) {
    throw new Error(`No lib*.d.ts files found in ${typescriptLibDir}`);
}

fs.mkdirSync(distDir, { recursive: true });
for (const entry of fs.readdirSync(distDir, { withFileTypes: true })) {
    if (entry.isFile() && LIB_FILE_PATTERN.test(entry.name)) {
        fs.unlinkSync(path.join(distDir, entry.name));
    }
}
for (const name of libFiles) {
    fs.copyFileSync(path.join(typescriptLibDir, name), path.join(distDir, name));
}
console.log(`Copied ${libFiles.length} TypeScript lib files into ${distDir}`);
