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
 * Conversion of TypeScript types into `TypeDto`.
 *
 * Two sources of type information:
 *  - syntactic `ts.TypeNode` annotations (preferred: predictable output), and
 *  - `ts.Type` objects from the TypeChecker (used for inference).
 *
 * Conventions verified against ArkAnalyzer ground truth (test resources repos/<project>/etsir):
 *  - a class/interface/enum declared in a project file -> ClassType{signature};
 *  - an enum MEMBER value -> EnumValueType{signature, name};
 *  - unresolved / ambient named types -> UnclearReferenceType{name, typeParameters};
 *  - anything exotic degrades to UnknownType (never crash).
 */

import * as ts from "typescript";
import { PATTERN_PARAMETER_PREFIX } from "../dto/constants";
import {
    ClassSignatureDto,
    FileSignatureDto,
    MethodParameterDto,
    MethodSignatureDto,
    NamespaceSignatureDto,
    UNKNOWN_CLASS_SIGNATURE,
} from "../dto/signatures";
import {
    ANY_TYPE,
    ArrayTypeDto,
    BOOLEAN_TYPE,
    ClassTypeDto,
    GenericTypeDto,
    NEVER_TYPE,
    NULL_TYPE,
    NUMBER_TYPE,
    STRING_TYPE,
    TypeDto,
    UNDEFINED_TYPE,
    UNKNOWN_TYPE,
    UnclearReferenceTypeDto,
    VOID_TYPE,
} from "../dto/types";

/** Guard against deeply nested / self-referential types. */
const MAX_DEPTH = 8;

export class TypeConverter {
    constructor(
        private readonly checker: ts.TypeChecker,
        private readonly fileSignatureFor: (sf: ts.SourceFile) => FileSignatureDto,
    ) {}

    // ------------------------------------------------------------------
    // Signatures
    // ------------------------------------------------------------------

    /** Class-like signature (class / interface / enum / struct) with its namespace chain. */
    classSignatureOf(decl: ts.Declaration & { name?: ts.DeclarationName }): ClassSignatureDto {
        const name = decl.name !== undefined && ts.isIdentifier(decl.name) ? decl.name.text : "";
        const signature: ClassSignatureDto = {
            name,
            declaringFile: this.fileSignatureFor(decl.getSourceFile()),
        };
        const ns = this.namespaceSignatureOf(decl);
        if (ns !== undefined) {
            signature.declaringNamespace = ns;
        }
        return signature;
    }

    /** Namespace chain of a declaration (innermost first), or undefined at file level. */
    namespaceSignatureOf(node: ts.Node): NamespaceSignatureDto | undefined {
        let current: ts.Node | undefined = node.parent;
        while (current !== undefined) {
            if (ts.isModuleDeclaration(current) && ts.isIdentifier(current.name)) {
                const signature: NamespaceSignatureDto = {
                    name: current.name.text,
                    declaringFile: this.fileSignatureFor(current.getSourceFile()),
                };
                const outer = this.namespaceSignatureOf(current);
                if (outer !== undefined) {
                    signature.declaringNamespace = outer;
                }
                return signature;
            }
            current = current.parent;
        }
        return undefined;
    }

    // ------------------------------------------------------------------
    // Syntactic conversion (type annotations)
    // ------------------------------------------------------------------

    convertTypeNode(node: ts.TypeNode | undefined, depth: number = 0): TypeDto {
        if (node === undefined) {
            return UNKNOWN_TYPE;
        }
        if (depth > MAX_DEPTH) {
            return UNKNOWN_TYPE;
        }
        try {
            return this.convertTypeNodeImpl(node, depth);
        } catch {
            return UNKNOWN_TYPE;
        }
    }

    private convertTypeNodeImpl(node: ts.TypeNode, depth: number): TypeDto {
        switch (node.kind) {
            case ts.SyntaxKind.AnyKeyword:
                return ANY_TYPE;
            case ts.SyntaxKind.UnknownKeyword:
                return UNKNOWN_TYPE;
            case ts.SyntaxKind.BooleanKeyword:
                return BOOLEAN_TYPE;
            case ts.SyntaxKind.NumberKeyword:
            case ts.SyntaxKind.BigIntKeyword:
                return NUMBER_TYPE;
            case ts.SyntaxKind.StringKeyword:
                return STRING_TYPE;
            case ts.SyntaxKind.VoidKeyword:
                return VOID_TYPE;
            case ts.SyntaxKind.NeverKeyword:
                return NEVER_TYPE;
            case ts.SyntaxKind.UndefinedKeyword:
                return UNDEFINED_TYPE;
            default:
                break;
        }

        if (ts.isLiteralTypeNode(node)) {
            return this.convertLiteralTypeNode(node);
        }
        if (ts.isParenthesizedTypeNode(node)) {
            return this.convertTypeNode(node.type, depth);
        }
        if (ts.isArrayTypeNode(node)) {
            return foldArray(this.convertTypeNode(node.elementType, depth + 1));
        }
        if (ts.isTupleTypeNode(node)) {
            return {
                _: "TupleType",
                types: node.elements.map((e) => this.convertTypeNode(unwrapTupleMember(e), depth + 1)),
            };
        }
        if (ts.isUnionTypeNode(node)) {
            return { _: "UnionType", types: node.types.map((t) => this.convertTypeNode(t, depth + 1)) };
        }
        if (ts.isIntersectionTypeNode(node)) {
            return { _: "IntersectionType", types: node.types.map((t) => this.convertTypeNode(t, depth + 1)) };
        }
        if (ts.isFunctionTypeNode(node)) {
            return {
                _: "FunctionType",
                signature: this.functionSignatureFromTypeNode(node, depth),
            };
        }
        if (ts.isTypeReferenceNode(node)) {
            return this.convertTypeReference(node, depth);
        }
        if (ts.isTemplateLiteralTypeNode(node)) {
            return STRING_TYPE;
        }
        // keyof/typeof/indexed access/conditional/mapped/type literals etc.
        return UNKNOWN_TYPE;
    }

    private convertLiteralTypeNode(node: ts.LiteralTypeNode): TypeDto {
        const literal = node.literal;
        if (literal.kind === ts.SyntaxKind.NullKeyword) {
            return NULL_TYPE;
        }
        if (literal.kind === ts.SyntaxKind.TrueKeyword) {
            return { _: "LiteralType", literal: true };
        }
        if (literal.kind === ts.SyntaxKind.FalseKeyword) {
            return { _: "LiteralType", literal: false };
        }
        if (ts.isStringLiteral(literal)) {
            return { _: "LiteralType", literal: literal.text };
        }
        if (ts.isNumericLiteral(literal)) {
            return numericLiteralType(Number(literal.text));
        }
        if (
            ts.isPrefixUnaryExpression(literal) &&
            literal.operator === ts.SyntaxKind.MinusToken &&
            ts.isNumericLiteral(literal.operand)
        ) {
            return numericLiteralType(-Number(literal.operand.text));
        }
        return UNKNOWN_TYPE;
    }

    private convertTypeReference(node: ts.TypeReferenceNode, depth: number): TypeDto {
        const name = entityNameToString(node.typeName);
        const typeArgs = node.typeArguments?.map((t) => this.convertTypeNode(t, depth + 1));

        // `Array<T>` is a proper array type.
        if (name === "Array" && typeArgs !== undefined && typeArgs.length === 1) {
            return foldArray(typeArgs[0]);
        }

        const symbol = this.resolveSymbol(node.typeName);
        if (symbol !== undefined) {
            const classDecl = findClassLikeDeclaration(symbol);
            if (classDecl !== undefined && isProjectDeclaration(classDecl)) {
                const result: ClassTypeDto = { _: "ClassType", signature: this.classSignatureOf(classDecl) };
                if (typeArgs !== undefined && typeArgs.length > 0) {
                    result.typeParameters = typeArgs;
                }
                return result;
            }
            const typeParamDecl = symbol.declarations?.find(ts.isTypeParameterDeclaration);
            if (typeParamDecl !== undefined) {
                // At USAGE sites a type parameter is just a name;
                // constraint/default are emitted only in typeParameters declarations.
                return { _: "GenericType", name: typeParamDecl.name.text };
            }
            const aliasDecl = symbol.declarations?.find(ts.isTypeAliasDeclaration);
            if (aliasDecl !== undefined && isProjectDeclaration(aliasDecl) && depth < MAX_DEPTH) {
                // Follow the alias target (AliasType with a LocalSignature would require
                // the declaring-method context; the resolved target is more useful downstream).
                return this.convertTypeNode(aliasDecl.type, depth + 1);
            }
        }

        const result: UnclearReferenceTypeDto = { _: "UnclearReferenceType", name };
        if (typeArgs !== undefined && typeArgs.length > 0) {
            result.typeParameters = typeArgs;
        }
        return result;
    }

    /** Type parameter declaration -> GenericType{name, constraint?, defaultType?}. */
    convertTypeParameter(decl: ts.TypeParameterDeclaration, depth: number = 0): GenericTypeDto {
        const result: GenericTypeDto = { _: "GenericType", name: decl.name.text };
        if (decl.constraint !== undefined) {
            result.constraint = this.convertTypeNode(decl.constraint, depth + 1);
        }
        if (decl.default !== undefined) {
            result.defaultType = this.convertTypeNode(decl.default, depth + 1);
        }
        return result;
    }

    /** Type parameter list of a class/method, or undefined when absent. */
    convertTypeParameters(
        decls: readonly ts.TypeParameterDeclaration[] | undefined,
    ): TypeDto[] | undefined {
        if (decls === undefined || decls.length === 0) {
            return undefined;
        }
        return decls.map((d) => this.convertTypeParameter(d));
    }

    private functionSignatureFromTypeNode(node: ts.FunctionTypeNode, depth: number): MethodSignatureDto {
        const parameters: MethodParameterDto[] = node.parameters.map((p, index) => {
            const param: MethodParameterDto = {
                name: ts.isIdentifier(p.name) ? p.name.text : `${PATTERN_PARAMETER_PREFIX}${index}`,
                type: this.convertTypeNode(p.type, depth + 1),
            };
            if (p.questionToken !== undefined) {
                param.isOptional = true;
            }
            if (p.dotDotDotToken !== undefined) {
                param.isRest = true;
            }
            return param;
        });
        return {
            declaringClass: UNKNOWN_CLASS_SIGNATURE,
            name: "",
            parameters,
            returnType: this.convertTypeNode(node.type, depth + 1),
        };
    }

    /**
     * Single implementation of "symbol of a name, with import aliases unwrapped".
     * Never throws: an unresolved name simply has no symbol.
     *
     * The symbol of a shorthand-property name (`{ value }`) belongs to the generated
     * object field, so it is redirected to the value symbol — that way the shorthand
     * follows the same storage path as an ordinary read of `value`.
     */
    symbolOf(node: ts.Node): ts.Symbol | undefined {
        try {
            let symbol = this.checker.getSymbolAtLocation(node);
            const parent = node.parent;
            if (parent !== undefined && ts.isShorthandPropertyAssignment(parent) && parent.name === node) {
                symbol = this.checker.getShorthandAssignmentValueSymbol(parent) ?? symbol;
            }
            if (symbol !== undefined && (symbol.flags & ts.SymbolFlags.Alias) !== 0) {
                return this.checker.getAliasedSymbol(symbol);
            }
            return symbol;
        } catch {
            return undefined;
        }
    }

    private resolveSymbol(name: ts.EntityName): ts.Symbol | undefined {
        return this.symbolOf(name);
    }

    // ------------------------------------------------------------------
    // Checker-based conversion (inference)
    // ------------------------------------------------------------------

    /** Resolve the (possibly inferred) type of a node. */
    typeOfNode(node: ts.Node): TypeDto {
        try {
            return this.convertType(this.checker.getTypeAtLocation(node));
        } catch {
            return UNKNOWN_TYPE;
        }
    }

    /** Resolve the contextual type supplied by an assignment/argument site. */
    contextualTypeOfNode(node: ts.Expression): TypeDto {
        try {
            return this.convertType(this.checker.getContextualType(node));
        } catch {
            return UNKNOWN_TYPE;
        }
    }

    convertType(type: ts.Type | undefined, depth: number = 0): TypeDto {
        if (type === undefined || depth > MAX_DEPTH) {
            return UNKNOWN_TYPE;
        }
        try {
            return this.convertTypeImpl(type, depth);
        } catch {
            return UNKNOWN_TYPE;
        }
    }

    private convertTypeImpl(type: ts.Type, depth: number): TypeDto {
        const flags = type.flags;

        if (flags & ts.TypeFlags.Any) return ANY_TYPE;
        if (flags & ts.TypeFlags.Unknown) return UNKNOWN_TYPE;
        if (flags & ts.TypeFlags.Void) return VOID_TYPE;
        if (flags & ts.TypeFlags.Never) return NEVER_TYPE;
        if (flags & ts.TypeFlags.Null) return NULL_TYPE;
        if (flags & ts.TypeFlags.Undefined) return UNDEFINED_TYPE;

        // Enum member values -> EnumValueType (checked before generic literals:
        // enum members carry literal flags too).
        const enumValue = this.tryEnumValueType(type);
        if (enumValue !== undefined) {
            return enumValue;
        }

        if (flags & ts.TypeFlags.StringLiteral) {
            return { _: "LiteralType", literal: (type as ts.StringLiteralType).value };
        }
        if (flags & ts.TypeFlags.NumberLiteral) {
            return numericLiteralType((type as ts.NumberLiteralType).value);
        }
        if (flags & ts.TypeFlags.BooleanLiteral) {
            const intrinsicName = (type as unknown as { intrinsicName?: string }).intrinsicName;
            return { _: "LiteralType", literal: intrinsicName === "true" };
        }

        if (flags & ts.TypeFlags.BooleanLike) return BOOLEAN_TYPE;
        if (flags & ts.TypeFlags.NumberLike) return NUMBER_TYPE;
        if (flags & ts.TypeFlags.BigIntLike) return NUMBER_TYPE;
        if (flags & ts.TypeFlags.StringLike) return STRING_TYPE;

        if (flags & ts.TypeFlags.TypeParameter) {
            return { _: "GenericType", name: this.typeName(type) };
        }

        if (type.isUnion()) {
            return { _: "UnionType", types: type.types.map((t) => this.convertType(t, depth + 1)) };
        }
        if (type.isIntersection()) {
            return { _: "IntersectionType", types: type.types.map((t) => this.convertType(t, depth + 1)) };
        }

        if (this.checker.isArrayType(type)) {
            const element = this.checker.getTypeArguments(type as ts.TypeReference)[0];
            return foldArray(this.convertType(element, depth + 1));
        }
        if (this.checker.isTupleType(type)) {
            const elements = this.checker.getTypeArguments(type as ts.TypeReference);
            return { _: "TupleType", types: elements.map((t) => this.convertType(t, depth + 1)) };
        }

        if (flags & ts.TypeFlags.Object) {
            return this.convertObjectType(type as ts.ObjectType, depth);
        }

        return UNKNOWN_TYPE;
    }

    private tryEnumValueType(type: ts.Type): TypeDto | undefined {
        const symbol = type.symbol as ts.Symbol | undefined;
        const memberDecl = symbol?.declarations?.find(ts.isEnumMember);
        if (memberDecl === undefined) {
            return undefined;
        }
        const enumDecl = memberDecl.parent;
        if (!isProjectDeclaration(enumDecl)) {
            return undefined;
        }
        return {
            _: "EnumValueType",
            signature: this.classSignatureOf(enumDecl),
            name: ts.isIdentifier(memberDecl.name) ? memberDecl.name.text : memberDecl.name.getText(),
        };
    }

    private convertObjectType(type: ts.ObjectType, depth: number): TypeDto {
        const symbol = type.symbol as ts.Symbol | undefined;
        const name = symbol?.getName();

        // Instances of project classes/interfaces/enums -> ClassType.
        const classDecl = symbol !== undefined ? findClassLikeDeclaration(symbol) : undefined;
        if (classDecl !== undefined && isProjectDeclaration(classDecl)) {
            const typeArgs =
                (type.objectFlags & ts.ObjectFlags.Reference) !== 0
                    ? this.checker.getTypeArguments(type as ts.TypeReference)
                    : [];
            const result: ClassTypeDto = { _: "ClassType", signature: this.classSignatureOf(classDecl) };
            if (typeArgs.length > 0) {
                result.typeParameters = typeArgs.map((t) => this.convertType(t, depth + 1));
            }
            return result;
        }

        // Function-like object types (function values, arrows, methods) -> FunctionType.
        const callSignatures = type.getCallSignatures();
        if (callSignatures.length > 0) {
            return this.functionTypeFromSignature(callSignatures[0], depth);
        }

        if (symbol !== undefined && name !== undefined && name !== "__type" && name !== "__object") {
            const typeArgs =
                (type.objectFlags & ts.ObjectFlags.Reference) !== 0
                    ? this.checker.getTypeArguments(type as ts.TypeReference)
                    : [];
            const result: UnclearReferenceTypeDto = { _: "UnclearReferenceType", name };
            if (typeArgs.length > 0) {
                result.typeParameters = typeArgs.map((t) => this.convertType(t, depth + 1));
            }
            return result;
        }

        return UNKNOWN_TYPE;
    }

    private functionTypeFromSignature(signature: ts.Signature, depth: number): TypeDto {
        try {
            const declaration = signature.getDeclaration() as ts.SignatureDeclaration | undefined;
            const parameters: MethodParameterDto[] = signature.getParameters().map((p) => {
                const paramDecl = p.valueDeclaration;
                let paramType: TypeDto = UNKNOWN_TYPE;
                if (paramDecl !== undefined) {
                    paramType = this.convertType(
                        this.checker.getTypeOfSymbolAtLocation(p, paramDecl),
                        depth + 1,
                    );
                }
                const param: MethodParameterDto = { name: p.getName(), type: paramType };
                if (paramDecl !== undefined && ts.isParameter(paramDecl)) {
                    if (paramDecl.questionToken !== undefined) {
                        param.isOptional = true;
                    }
                    if (paramDecl.dotDotDotToken !== undefined) {
                        param.isRest = true;
                    }
                }
                return param;
            });
            const returnType = this.convertType(this.checker.getReturnTypeOfSignature(signature), depth + 1);
            const name =
                declaration !== undefined &&
                (ts.isFunctionDeclaration(declaration) || ts.isMethodDeclaration(declaration)) &&
                declaration.name !== undefined &&
                ts.isIdentifier(declaration.name)
                    ? declaration.name.text
                    : "";
            return {
                _: "FunctionType",
                signature: {
                    declaringClass: UNKNOWN_CLASS_SIGNATURE,
                    name,
                    parameters,
                    returnType,
                },
            };
        } catch {
            return UNKNOWN_TYPE;
        }
    }

    private typeName(type: ts.Type): string {
        if (type.symbol !== undefined) {
            return type.symbol.getName();
        }
        try {
            return this.checker.typeToString(type);
        } catch {
            return "%unk";
        }
    }
}

// ----------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------

/**
 * Non-finite numbers have no JSON representation (`JSON.stringify` writes `null`),
 * which the Kotlin `PrimitiveLiteralSerializer` cannot map back to a number,
 * so such literal types degrade to a plain `number`.
 */
function numericLiteralType(value: number): TypeDto {
    return Number.isFinite(value) ? { _: "LiteralType", literal: value } : NUMBER_TYPE;
}

/** `T[][]` folds into ArrayType{T, dimensions: 2}. */
function foldArray(element: TypeDto): ArrayTypeDto {
    if (element._ === "ArrayType") {
        return { _: "ArrayType", elementType: element.elementType, dimensions: element.dimensions + 1 };
    }
    return { _: "ArrayType", elementType: element, dimensions: 1 };
}

function unwrapTupleMember(node: ts.TypeNode): ts.TypeNode {
    if (ts.isNamedTupleMember(node)) {
        return node.type;
    }
    return node;
}

function entityNameToString(name: ts.EntityName): string {
    if (ts.isIdentifier(name)) {
        return name.text;
    }
    return `${entityNameToString(name.left)}.${name.right.text}`;
}

/** Class-like declaration of a symbol (class / interface / enum). */
function findClassLikeDeclaration(
    symbol: ts.Symbol,
): (ts.ClassDeclaration | ts.InterfaceDeclaration | ts.EnumDeclaration) | undefined {
    return symbol.declarations?.find(
        (d): d is ts.ClassDeclaration | ts.InterfaceDeclaration | ts.EnumDeclaration =>
            ts.isClassDeclaration(d) || ts.isInterfaceDeclaration(d) || ts.isEnumDeclaration(d),
    );
}

/**
 * Whether a declaration belongs to project code (as opposed to ambient/lib `.d.ts`).
 * Ambient types are emitted as UnclearReferenceType — same as ArkAnalyzer does
 * for types not belonging to the analyzed project.
 */
function isProjectDeclaration(decl: ts.Declaration): boolean {
    return !decl.getSourceFile().isDeclarationFile;
}
