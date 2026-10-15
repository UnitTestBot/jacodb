import { describe, expect, it } from "vitest";
import { serializeEtsFile } from "../src/serialize";
import { LexicalEnvTypeDefinitionDto, TypeDto } from "../src/dto/types";
import { defaultMethod, lower } from "./util";

describe("wire format", () => {
    it("an empty file serializes to the exact %dflt skeleton", () => {
        const { file } = lower("", "myProject", "src/foo.ts");
        const json = JSON.parse(serializeEtsFile(file));

        const fileSig = { projectName: "myProject", fileName: "src/foo.ts" };
        const classSig = { name: "%dflt", declaringFile: fileSig };
        const classType = { _: "ClassType", signature: classSig };
        expect(json).toEqual({
            signature: fileSig,
            namespaces: [],
            classes: [
                {
                    signature: classSig,
                    modifiers: 0,
                    decorators: [],
                    category: 0,
                    superClassName: "",
                    implementedInterfaceNames: [],
                    fields: [],
                    methods: [
                        {
                            signature: {
                                declaringClass: classSig,
                                name: "%dflt",
                                parameters: [],
                                returnType: { _: "VoidType" },
                            },
                            modifiers: 0,
                            decorators: [],
                            isStrictMode: false,
                            body: {
                                locals: [{ name: "this", type: classType }],
                                cfg: {
                                    blocks: [
                                        {
                                            id: 0,
                                            successors: [],
                                            predecessors: [],
                                            stmts: [
                                                {
                                                    _: "AssignStmt",
                                                    left: { _: "Local", name: "this", type: classType },
                                                    right: { _: "ThisRef", type: classType },
                                                },
                                                { _: "ReturnVoidStmt" },
                                            ],
                                        },
                                    ],
                                },
                            },
                        },
                    ],
                },
            ],
            importInfos: [],
            exportInfos: [],
        });
    });

    it("drops undefined optional fields but keeps nulls", () => {
        const { file } = lower("");
        file.classes[0].typeParameters = undefined;
        file.classes[0].superClassName = null;
        const json = JSON.parse(serializeEtsFile(file));
        expect("typeParameters" in json.classes[0]).toBe(false);
        expect(json.classes[0].superClassName).toBeNull();
    });

    it("does not emit a discriminator for body.locals entries", () => {
        const { file } = lower("");
        const json = JSON.parse(serializeEtsFile(file));
        const local = json.classes[0].methods[0].body.locals[0];
        expect(Object.keys(local).sort()).toEqual(["name", "type"]);
    });

    it("does not emit local discriminators in concrete lexical-environment fields", () => {
        const { file } = lower(`
            function outer(value: number): () => number { return () => value; }
        `);
        const json = JSON.parse(serializeEtsFile(file));
        const closure = json.classes[0].methods.find((method: { signature: { name: string } }) =>
            method.signature.name.startsWith("%AM"),
        );
        const environmentReference = closure.signature.parameters[0].type;
        const environmentType = json.lexicalEnvTypes[environmentReference.id];
        const closureLoad = closure.body.cfg.blocks[0].stmts.find(
            (stmt: { right?: { _?: string } }) => stmt.right?._ === "ClosureFieldRef",
        );
        expect(Object.keys(environmentType.closures[0]).sort()).toEqual(["name", "type"]);
        expect(Object.keys(closureLoad.right.base).sort()).toEqual(["name", "type"]);
    });

    it("serializes shared nested environments in linear space without omitting captures", () => {
        const { file } = lower("");
        const owner = defaultMethod(file).signature;
        let type: TypeDto = { _: "NumberType" };
        for (let index = 0; index < 25; index++) {
            type = {
                _: "LexicalEnvType",
                method: { ...owner, name: `closure-${index}` },
                closures: [{ name: "left", type }, { name: "right", type }],
            };
        }
        defaultMethod(file).body!.locals.push({ name: "environment", type });

        const serialized = serializeEtsFile(file);
        const compact = JSON.parse(serialized);

        expect(serialized.length).toBeLessThan(20_000);
        expect(compact.lexicalEnvTypes).toHaveLength(25);
        expect(compact.lexicalEnvTypes.every((environment: LexicalEnvTypeDefinitionDto) =>
            environment.closures.map((closure) => closure.name).join(",") === "left,right",
        )).toBe(true);
        expect(compact.lexicalEnvTypes.every((environment: object) => !("_" in environment))).toBe(true);
        expect(type._).toBe("LexicalEnvType");
        expect(serializeEtsFile(compact)).toBe(serialized);
    });

    it("preserves the complete original nested environment graph through table references", () => {
        const { file } = lower(`
            function outer(value: number) {
                const first = () => value;
                return () => first();
            }
        `);
        const compact = JSON.parse(serializeEtsFile(file));
        const expand = (value: unknown): unknown => {
            if (value === null || typeof value !== "object") return value;
            if ((value as { _?: string })._ === "LexicalEnvTypeRef") {
                return { _: "LexicalEnvType", ...expand(compact.lexicalEnvTypes[(value as { id: number }).id]) as object };
            }
            if (Array.isArray(value)) return value.map(expand);
            return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, expand(child)]));
        };
        const { lexicalEnvTypes, ...withoutTable } = compact;

        expect(lexicalEnvTypes.length).toBeGreaterThan(1);
        expect(expand(withoutTable)).toEqual(JSON.parse(JSON.stringify(file)));
    });
});
