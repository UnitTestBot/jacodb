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
 * Signature DTOs. Mirror `org.jacodb.ets.dto` signatures
 * (jacodb-ets/src/main/kotlin/org/jacodb/ets/dto/Signatures.kt).
 *
 * NOTE: signatures are serialized NON-polymorphically on the Kotlin side,
 * so they carry no "_" discriminator.
 */

import { TypeDto } from "./types";

export interface FileSignatureDto {
    projectName: string;
    fileName: string;
}

export interface NamespaceSignatureDto {
    name: string;
    declaringFile: FileSignatureDto;
    declaringNamespace?: NamespaceSignatureDto;
}

export interface ClassSignatureDto {
    name: string;
    declaringFile: FileSignatureDto;
    declaringNamespace?: NamespaceSignatureDto;
}

export interface FieldSignatureDto {
    declaringClass: ClassSignatureDto;
    name: string;
    type: TypeDto;
}

export interface MethodParameterDto {
    name: string;
    type: TypeDto;
    isOptional?: boolean; // Kotlin default: false
    isRest?: boolean; // Kotlin default: false
}

export interface MethodSignatureDto {
    declaringClass: ClassSignatureDto;
    name: string;
    parameters: MethodParameterDto[];
    returnType: TypeDto;
}

export interface LocalSignatureDto {
    name: string;
    method: MethodSignatureDto;
}

/** Signature placeholders mirroring `EtsFileSignature.UNKNOWN` / `EtsClassSignature.UNKNOWN` semantics. */
export const UNKNOWN_FILE_SIGNATURE: FileSignatureDto = {
    projectName: "%unk",
    fileName: "%unk",
};

export const UNKNOWN_CLASS_SIGNATURE: ClassSignatureDto = {
    name: "",
    declaringFile: UNKNOWN_FILE_SIGNATURE,
};
