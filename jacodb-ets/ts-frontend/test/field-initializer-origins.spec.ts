import { describe, expect, it } from "vitest";
import { BodyDto, EtsFileDto } from "../src/dto/model";
import { serializeEtsFile } from "../src/serialize";
import { lower } from "./util";

function initializer(file: EtsFileDto, name: string): BodyDto {
    const clazz = file.classes.find((candidate) => candidate.signature.name === "Fields");
    const method = clazz?.methods.find((candidate) => candidate.signature.name === name);
    if (method?.body === undefined) throw new Error(`Missing initializer ${name}`);
    return method.body;
}

function expectFieldOrigins(body: BodyDto, source: string, declarations: Readonly<Record<string, string>>): void {
    const found = new Set<string>();
    for (const block of body.cfg.blocks) {
        block.stmts.forEach((stmt, stmtIndex) => {
            const origin = body.stmtOrigins?.find((candidate) =>
                candidate.blockId === block.id && candidate.stmtIndex === stmtIndex,
            );
            const field = stmt._ === "AssignStmt"
                && (stmt.left._ === "InstanceFieldRef" || stmt.left._ === "StaticFieldRef") ? stmt.left.field : undefined;
            if (field !== undefined) {
                const name = field.name;
                const declaration = declarations[name];
                if (declaration === undefined) return;

                found.add(name);
                expect(origin?.source.nodeKind, name).toBe("PropertyDeclaration");
                expect(source.slice(origin!.source.startOffset, origin!.source.endOffset), name).toBe(declaration);
            }

            const isPrologue = stmt._ === "AssignStmt" && (stmt.right._ === "ThisRef" || stmt.right._ === "ParameterRef");
            if (isPrologue || stmt._ === "ReturnVoidStmt") {
                expect(origin, "synthetic prologue/implicit return must remain unmapped").toBeUndefined();
            }
        });
    }

    expect([...found].sort()).toEqual(Object.keys(declarations).sort());
}

describe("real field initializer source origins", () => {
    it("maps instance literal and arrow stores to their actual property declarations", () => {
        const source = `
            export class Fields {
                public count = 1;
                flag = false;
                action = () => 17;
            }
        `;
        const { file } = lower(source);
        const serialized = JSON.parse(serializeEtsFile(file)) as EtsFileDto;

        expectFieldOrigins(initializer(serialized, "%instInit"), source, {
            count: "public count = 1;",
            flag: "flag = false;",
            action: "action = () => 17;",
        });
    });

    it("maps static literal and arrow stores without attributing synthetic setup or return", () => {
        const source = `
            export class Fields {
                static count = 2;
                static flag = true;
                static action = () => 23;
            }
        `;
        const { file } = lower(source);
        const serialized = JSON.parse(serializeEtsFile(file)) as EtsFileDto;

        expectFieldOrigins(initializer(serialized, "%statInit"), source, {
            count: "static count = 2;",
            flag: "static flag = true;",
            action: "static action = () => 23;",
        });
    });

    it("keeps inner expression origins while mapping the enclosing field stores", () => {
        const source = `
            function value() { return 3; }
            export class Fields {
                computed = value() + 1;
                static computed = value() > 0 ? value() : 0;
            }
        `;
        const { file } = lower(source);

        expectFieldOrigins(initializer(file, "%instInit"), source, { computed: "computed = value() + 1;" });
        expectFieldOrigins(initializer(file, "%statInit"), source, {
            computed: "static computed = value() > 0 ? value() : 0;",
        });
        const innerOrigins = initializer(file, "%statInit").stmtOrigins!.filter((origin) =>
            origin.source.nodeKind !== "PropertyDeclaration",
        );
        expect(innerOrigins.length).toBeGreaterThan(0);
        expect(innerOrigins.every((origin) => source.slice(origin.source.startOffset, origin.source.endOffset).length > 0))
            .toBe(true);
    });
});
