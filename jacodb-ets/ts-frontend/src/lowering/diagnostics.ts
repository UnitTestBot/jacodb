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

/**
 * Diagnostics collection + Raw* fallback emission.
 *
 * The frontend must never fail on exotic-but-parseable input: anything we cannot
 * lower degrades to a raw statement/value with an "Unsupported*" discriminator,
 * which the Kotlin side deserializes into RawStmtDto / RawValueDto automatically.
 */

import * as ts from "typescript";
import { StmtDto } from "../dto/stmts";
import { TypeDto, UNKNOWN_TYPE } from "../dto/types";
import { ValueDto } from "../dto/values";

export class Diagnostics {
    readonly messages: string[] = [];

    warn(node: ts.Node | undefined, message: string): void {
        let location = "";
        if (node !== undefined) {
            try {
                const sf = node.getSourceFile();
                const { line, character } = sf.getLineAndCharacterOfPosition(node.getStart());
                location = `${sf.fileName}:${line + 1}:${character + 1}: `;
            } catch {
                // synthetic node without position
            }
        }
        this.messages.push(`${location}${message}`);
    }
}

function snippet(node: ts.Node): string {
    try {
        return node.getText().slice(0, 200);
    } catch {
        return "<synthetic>";
    }
}

/** Fallback statement: deserialized by Kotlin as RawStmt(kind="UnsupportedStmt"). */
export function unsupportedStmt(node: ts.Node): StmtDto {
    return {
        _: "UnsupportedStmt",
        kindName: ts.SyntaxKind[node.kind],
        text: snippet(node),
    } as unknown as StmtDto;
}

/** Fallback value: deserialized by Kotlin as RawValue(kind="UnsupportedValue"); `type` is required. */
export function unsupportedValue(node: ts.Node, type: TypeDto = UNKNOWN_TYPE): ValueDto {
    return {
        _: "UnsupportedValue",
        kindName: ts.SyntaxKind[node.kind],
        text: snippet(node),
        type,
    } as unknown as ValueDto;
}
