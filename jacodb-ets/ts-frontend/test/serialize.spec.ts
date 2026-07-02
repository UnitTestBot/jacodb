import { describe, expect, it } from "vitest";
import { buildSkeletonFile } from "../src/index";
import { serializeEtsFile } from "../src/serialize";
import { validateEtsFile } from "../src/validate";

describe("skeleton EtsFileDto", () => {
    it("is valid and serializes to the exact wire format", () => {
        const file = buildSkeletonFile("myProject", "src/foo.ts");
        expect(validateEtsFile(file)).toEqual([]);

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
        const file = buildSkeletonFile("p", "f.ts");
        file.classes[0].typeParameters = undefined;
        file.classes[0].superClassName = null;
        const json = JSON.parse(serializeEtsFile(file));
        expect("typeParameters" in json.classes[0]).toBe(false);
        expect(json.classes[0].superClassName).toBeNull();
    });

    it("does not emit a discriminator for body.locals entries", () => {
        const file = buildSkeletonFile("p", "f.ts");
        const json = JSON.parse(serializeEtsFile(file));
        const local = json.classes[0].methods[0].body.locals[0];
        expect(Object.keys(local).sort()).toEqual(["name", "type"]);
    });
});
