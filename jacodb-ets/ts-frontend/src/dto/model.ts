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
 * Top-level DTOs. Mirror `org.jacodb.ets.dto` model
 * (jacodb-ets/src/main/kotlin/org/jacodb/ets/dto/Model.kt and Cfg.kt).
 */

import { ClassCategoryValue, ExportTypeValue, ImportType } from "./constants";
import {
    ClassSignatureDto,
    FieldSignatureDto,
    MethodParameterDto,
    MethodSignatureDto,
    NamespaceSignatureDto,
    FileSignatureDto,
} from "./signatures";
import { StmtDto } from "./stmts";
import { TypeDto } from "./types";

export interface EtsFileDto {
    signature: FileSignatureDto;
    namespaces: NamespaceDto[];
    classes: ClassDto[];
    importInfos: ImportInfoDto[];
    exportInfos: ExportInfoDto[];
}

export interface NamespaceDto {
    signature: NamespaceSignatureDto;
    classes?: ClassDto[]; // Kotlin default: empty list
    namespaces?: NamespaceDto[]; // Kotlin default: empty list
}

export interface ClassDto {
    signature: ClassSignatureDto;
    modifiers: number;
    decorators: DecoratorDto[];
    category?: ClassCategoryValue; // Kotlin default: 0 (CLASS)
    typeParameters?: TypeDto[]; // Kotlin default: null
    superClassName: string | null; // required by Kotlin (nullable, no default); "" means "no superclass"
    implementedInterfaceNames: string[];
    fields: FieldDto[];
    methods: MethodDto[];
}

export interface FieldDto {
    signature: FieldSignatureDto;
    modifiers: number;
    decorators: DecoratorDto[];
    questionToken: boolean; // '?'
    exclamationToken: boolean; // '!'
}

export interface MethodDto {
    signature: MethodSignatureDto;
    modifiers: number;
    decorators: DecoratorDto[];
    typeParameters?: TypeDto[]; // Kotlin default: null
    body?: BodyDto; // omitted for bodyless methods (interfaces, ambient declarations)
}

export interface BodyDto {
    locals: LocalDeclDto[];
    cfg: CfgDto;
}

/**
 * An entry of `body.locals`. Same shape as a Local value, but deserialized
 * NON-polymorphically on the Kotlin side (the list is typed `List<LocalDto>`),
 * so a "_" discriminator would be rejected as an unknown key by the strict Json.
 * Emit ONLY `name` and `type` here (matches ArkAnalyzer output).
 */
export interface LocalDeclDto {
    name: string;
    type: TypeDto;
}

export interface CfgDto {
    blocks: BasicBlockDto[];
}

export interface BasicBlockDto {
    id: number; // must equal its index in `blocks`; entry block has id 0
    successors: number[]; // for a block ending with IfStmt: [falseBranch, trueBranch]
    predecessors?: number[]; // Kotlin default: null (unused by Convert); we emit it for parity with ArkAnalyzer
    stmts: StmtDto[];
}

export interface ImportInfoDto {
    importName: string;
    importType: ImportType;
    importFrom: string;
    nameBeforeAs?: string; // Kotlin default: null
    modifiers: number;
}

export interface ExportInfoDto {
    exportName: string;
    exportType: ExportTypeValue;
    exportFrom?: string; // Kotlin default: null
    nameBeforeAs?: string; // Kotlin default: null
    modifiers: number;
}

export interface DecoratorDto {
    kind: string;
}
