import { describe, expect, it } from "vitest";
import { EtsFileDto } from "../src/dto/model";
import { LexicalEnvTypeDto } from "../src/dto/types";
import { serializeEtsFile } from "../src/serialize";
import { executeObjectIr } from "./object-runtime";
import { lower } from "./util";

function lowerSerialized(source: string): EtsFileDto {
    const { file, diagnostics } = lower(source);

    expect(diagnostics.messages).toEqual([]);
    return JSON.parse(serializeEtsFile(file));
}

function rewriteCaptures(value: unknown, rewrite: (type: LexicalEnvTypeDto) => void): number {
    if (value === null || typeof value !== "object") return 0;
    const record = value as Record<string, unknown>;
    let changed = 0;
    if (record._ === "LexicalEnvType") {
        const type = value as LexicalEnvTypeDto;
        changed = type.closures.length;
        rewrite(type);
    }

    for (const nested of Object.values(value)) changed += rewriteCaptures(nested, rewrite);
    return changed;
}

const DEFAULT_CAPTURE = `
    function make(seed: number) { return (value = seed) => value; }
    export function result() { return make(12)(); }
`;

describe("object test interpreter closure environments", () => {
    it("constructs the environment from the lifted method's declared slots", () => {
        const file = lowerSerialized(DEFAULT_CAPTURE);
        const lifted = file.classes.flatMap((clazz) => clazz.methods)
            .find((method) => method.signature.parameters[0]?.type._ === "LexicalEnvType")!;
        const environmentType = lifted.signature.parameters[0].type;
        if (environmentType._ !== "LexicalEnvType") throw new Error("expected a captured parameter");

        expect(executeObjectIr(file, "result")).toBe(12);
        environmentType.closures = [];

        expect(() => executeObjectIr(file, "result")).toThrow("undeclared test capture seed");
    });

    it("rejects a closure reference omitted from its base's declared slots", () => {
        const file = lowerSerialized(DEFAULT_CAPTURE);
        const reference = file.classes.flatMap((clazz) => clazz.methods)
            .flatMap((method) => method.body?.cfg.blocks.flatMap((block) => block.stmts) ?? [])
            .flatMap((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ClosureFieldRef" ? [stmt.right] : [])
            .find((value) => value.fieldName === "seed")!;
        const environmentType = reference.base.type;
        if (environmentType._ !== "LexicalEnvType") throw new Error("expected a captured reference");

        expect(executeObjectIr(file, "result")).toBe(12);
        environmentType.closures = [];

        expect(() => executeObjectIr(file, "result")).toThrow("undeclared test capture seed");
    });

    it.each(["removed", "renamed"])("rejects %s getter captures in serialized IR", (mutation) => {
        const file = lowerSerialized(`
            export function result(seed: number) {
                const object = { y: 2, get x() { seed++; return seed + this.y; } };
                const first = object.x;
                return first * 10 + object.x;
            }
        `);

        expect(executeObjectIr(file, "result", [3])).toBe(67);
        const changedSlots = rewriteCaptures(file, (type) => {
            type.closures = mutation === "removed"
                ? []
                : type.closures.map((slot) => ({ ...slot, name: `${slot.name}$missing` }));
        });

        expect(changedSlots).toBeGreaterThan(0);
        expect(() => executeObjectIr(file, "result", [3])).toThrow("undeclared test capture seed");
    });

    it("keeps outer writes and sibling closure writes visible through declared slots", () => {
        const file = lowerSerialized(`
            export function result() {
                let seed = 1;
                const increment = () => seed++;
                const read = () => seed;
                seed = 4;
                return increment() * 100 + read();
            }
        `);

        expect(executeObjectIr(file, "result")).toBe(405);
    });

    it("forwards a live environment through nested closures", () => {
        const file = lowerSerialized(`
            export function result() {
                let seed = 2;
                const outer = () => {
                    const inner = () => ++seed;
                    return inner;
                };
                const inner = outer();
                seed = 5;
                return inner() * 10 + seed;
            }
        `);

        expect(executeObjectIr(file, "result")).toBe(66);
    });
});
