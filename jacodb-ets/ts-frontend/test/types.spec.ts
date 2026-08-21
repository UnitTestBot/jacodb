import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { FileSignatureDto } from "../src/dto/signatures";
import { TypeDto } from "../src/dto/types";
import { TypeConverter } from "../src/types/convert";
import { compile, findNode, findVariable, lower, methodByName } from "./util";

const FILE_SIG: FileSignatureDto = { projectName: "proj", fileName: "test.ts" };

function makeConverter(source: string): { converter: TypeConverter; sourceFile: ts.SourceFile } {
    const { checker, sourceFile } = compile(source);
    const converter = new TypeConverter(checker, () => FILE_SIG);
    return { converter, sourceFile };
}

/** Convert the type ANNOTATION of variable `name`. */
function annotationOf(source: string, name: string = "x"): TypeDto {
    const { converter, sourceFile } = makeConverter(source);
    const decl = findVariable(sourceFile, name);
    return converter.convertTypeNode(decl.type);
}

/** Convert the INFERRED type of variable `name`. */
function inferredOf(source: string, name: string = "x"): TypeDto {
    const { converter, sourceFile } = makeConverter(source);
    const decl = findVariable(sourceFile, name);
    return converter.typeOfNode(decl.name);
}

describe("convertTypeNode (annotations)", () => {
    it("converts primitive keywords", () => {
        expect(annotationOf("let x: number;")).toEqual({ _: "NumberType" });
        expect(annotationOf("let x: string;")).toEqual({ _: "StringType" });
        expect(annotationOf("let x: boolean;")).toEqual({ _: "BooleanType" });
        expect(annotationOf("let x: any;")).toEqual({ _: "AnyType" });
        expect(annotationOf("let x: unknown;")).toEqual({ _: "UnknownType" });
        expect(annotationOf("let x: undefined;")).toEqual({ _: "UndefinedType" });
        expect(annotationOf("let x: never;")).toEqual({ _: "NeverType" });
        expect(annotationOf("let x: bigint;")).toEqual({ _: "NumberType" });
    });

    it("converts literal types with bare JSON primitives", () => {
        expect(annotationOf(`let x: "hello";`)).toEqual({ _: "LiteralType", literal: "hello" });
        expect(annotationOf("let x: 42;")).toEqual({ _: "LiteralType", literal: 42 });
        expect(annotationOf("let x: -1;")).toEqual({ _: "LiteralType", literal: -1 });
        expect(annotationOf("let x: true;")).toEqual({ _: "LiteralType", literal: true });
        expect(annotationOf("let x: null;")).toEqual({ _: "NullType" });
    });

    it("converts arrays and folds dimensions", () => {
        expect(annotationOf("let x: number[];")).toEqual({
            _: "ArrayType",
            elementType: { _: "NumberType" },
            dimensions: 1,
        });
        expect(annotationOf("let x: string[][];")).toEqual({
            _: "ArrayType",
            elementType: { _: "StringType" },
            dimensions: 2,
        });
        expect(annotationOf("let x: boolean[][][];")).toEqual({
            _: "ArrayType",
            elementType: { _: "BooleanType" },
            dimensions: 3,
        });
        expect(annotationOf("let x: number[][][][];")).toEqual({
            _: "ArrayType",
            elementType: { _: "NumberType" },
            dimensions: 4,
        });
        expect(annotationOf("let x: Array<number>;")).toEqual({
            _: "ArrayType",
            elementType: { _: "NumberType" },
            dimensions: 1,
        });
    });

    it("converts tuples", () => {
        expect(annotationOf("let x: [number, string];")).toEqual({
            _: "TupleType",
            types: [{ _: "NumberType" }, { _: "StringType" }],
        });
    });

    it("converts unions and intersections", () => {
        expect(annotationOf("let x: number | string;")).toEqual({
            _: "UnionType",
            types: [{ _: "NumberType" }, { _: "StringType" }],
        });
        expect(annotationOf("interface A {}\ninterface B {}\nlet x: A & B;")).toEqual({
            _: "IntersectionType",
            types: [
                { _: "ClassType", signature: { name: "A", declaringFile: FILE_SIG } },
                { _: "ClassType", signature: { name: "B", declaringFile: FILE_SIG } },
            ],
        });
    });

    it("converts project classes/interfaces/enums to ClassType", () => {
        expect(annotationOf("class C {}\nlet x: C;")).toEqual({
            _: "ClassType",
            signature: { name: "C", declaringFile: FILE_SIG },
        });
        expect(annotationOf("interface I { f(): void }\nlet x: I;")).toEqual({
            _: "ClassType",
            signature: { name: "I", declaringFile: FILE_SIG },
        });
        expect(annotationOf("enum E { A, B }\nlet x: E;")).toEqual({
            _: "ClassType",
            signature: { name: "E", declaringFile: FILE_SIG },
        });
    });

    it("passes generic arguments to ClassType", () => {
        expect(annotationOf("class Box<T> { v: T }\nlet x: Box<number>;")).toEqual({
            _: "ClassType",
            signature: { name: "Box", declaringFile: FILE_SIG },
            typeParameters: [{ _: "NumberType" }],
        });
    });

    it("converts ambient types to UnclearReferenceType", () => {
        expect(annotationOf("let x: Promise<number>;")).toEqual({
            _: "UnclearReferenceType",
            name: "Promise",
            typeParameters: [{ _: "NumberType" }],
        });
        expect(annotationOf("let x: SomethingUndeclared;")).toEqual({
            _: "UnclearReferenceType",
            name: "SomethingUndeclared",
        });
    });

    it("follows type aliases to their target", () => {
        expect(annotationOf("type MyNum = number;\nlet x: MyNum;")).toEqual({ _: "NumberType" });
    });

    it("instantiates generic aliases in lowered parameter types", () => {
        const { file } = lower("type Vec<T> = T[];\nfunction sum(values: Vec<number>): number { return values[0]; }");

        expect(methodByName(file, "sum").signature.parameters).toEqual([
            {
                name: "values",
                type: { _: "ArrayType", elementType: { _: "NumberType" }, dimensions: 1 },
            },
        ]);
    });

    it("does not inherit arguments on nested aliases without arguments", () => {
        const { file } = lower(
            "type Json<T = string> = T | Json[];\nfunction parse(json: Json<number>): void {}",
        );

        const parameterType = methodByName(file, "parse").signature.parameters[0].type;
        if (parameterType._ !== "UnionType") throw new Error("expected Json<number> to lower to UnionType");
        expect(parameterType.types[0]).toEqual({ _: "NumberType" });

        const nestedJson = parameterType.types[1];
        if (nestedJson._ !== "ArrayType" || nestedJson.elementType._ !== "UnionType") {
            throw new Error("expected nested Json to lower through ArrayType to UnionType");
        }
        expect(nestedJson.elementType.types[0]).toEqual({ _: "GenericType", name: "T" });
    });

    it("converts function types", () => {
        expect(annotationOf("let x: (a: number, b?: string) => boolean;")).toEqual({
            _: "FunctionType",
            signature: {
                declaringClass: { name: "", declaringFile: { projectName: "%unk", fileName: "%unk" } },
                name: "",
                parameters: [
                    { name: "a", type: { _: "NumberType" } },
                    { name: "b", type: { _: "StringType" }, isOptional: true },
                ],
                returnType: { _: "BooleanType" },
            },
        });
    });

    it("materializes object type literals as structural classes", () => {
        const { file } = lower(`
            class C {
                read(value: { required: number; optional?: string }): number {
                    return value.required;
                }
            }
        `);
        const parameterType = methodByName(file, "read").signature.parameters[0].type;
        expect(parameterType._).toBe("ClassType");
        if (parameterType._ !== "ClassType") throw new Error("expected a structural class type");

        const structuralClass = file.classes.find(
            (candidate) => candidate.signature.name === parameterType.signature.name,
        );
        expect(structuralClass).toBeDefined();
        expect(structuralClass?.fields).toEqual([
            expect.objectContaining({
                signature: expect.objectContaining({ name: "required", type: { _: "NumberType" } }),
                questionToken: false,
            }),
            expect.objectContaining({
                signature: expect.objectContaining({ name: "optional", type: { _: "StringType" } }),
                questionToken: true,
            }),
        ]);
    });

    it("resolves namespace-qualified names with the namespace chain", () => {
        const type = annotationOf("namespace N { export class C {} }\nlet x: N.C;");
        expect(type).toEqual({
            _: "ClassType",
            signature: {
                name: "C",
                declaringFile: FILE_SIG,
                declaringNamespace: { name: "N", declaringFile: FILE_SIG },
            },
        });
    });

    it("converts generic parameters of functions", () => {
        const { converter, sourceFile } = makeConverter("function f<T extends string>(v: T): T { return v; }");
        const fn = findNode(sourceFile, ts.isFunctionDeclaration)!;
        expect(converter.convertTypeParameters(fn.typeParameters)).toEqual([
            { _: "GenericType", name: "T", constraint: { _: "StringType" } },
        ]);
        expect(converter.convertTypeNode(fn.parameters[0].type)).toEqual({
            _: "GenericType",
            name: "T",
        });
    });

    it("degrades exotic types to UnknownType", () => {
        expect(annotationOf("let x: keyof { a: number };")).toEqual({ _: "UnknownType" });
        expect(annotationOf("let x;")).toEqual({ _: "UnknownType" });
        expect(annotationOf("let x: `a${string}`;")).toEqual({ _: "StringType" });
    });
});

describe("typeOfNode (inference)", () => {
    it("widens let-declarations to primitives", () => {
        expect(inferredOf("let x = 42;")).toEqual({ _: "NumberType" });
        expect(inferredOf(`let x = "s";`)).toEqual({ _: "StringType" });
        expect(inferredOf("let x = true;")).toEqual({ _: "BooleanType" });
    });

    it("keeps literal types for const-declarations", () => {
        expect(inferredOf("const x = 42;")).toEqual({ _: "LiteralType", literal: 42 });
        expect(inferredOf(`const x = "s";`)).toEqual({ _: "LiteralType", literal: "s" });
    });

    it("infers arrays", () => {
        expect(inferredOf("let x = [1, 2, 3];")).toEqual({
            _: "ArrayType",
            elementType: { _: "NumberType" },
            dimensions: 1,
        });
    });

    it("infers project class instances", () => {
        expect(inferredOf("class C {}\nlet x = new C();")).toEqual({
            _: "ClassType",
            signature: { name: "C", declaringFile: FILE_SIG },
        });
    });

    it("infers enum member access as EnumValueType", () => {
        expect(inferredOf("enum E { A, B }\nconst x = E.A;")).toEqual({
            _: "EnumValueType",
            signature: { name: "E", declaringFile: FILE_SIG },
            name: "A",
        });
    });

    it("infers function values as FunctionType", () => {
        const type = inferredOf("function f(a: number): string { return '' + a; }\nconst x = f;");
        expect(type).toMatchObject({
            _: "FunctionType",
            signature: {
                name: "f",
                parameters: [{ name: "a", type: { _: "NumberType" } }],
                returnType: { _: "StringType" },
            },
        });
    });

    it("never throws on weird nodes", () => {
        expect(inferredOf("let x = Symbol('s');")).toBeDefined();
    });
});
