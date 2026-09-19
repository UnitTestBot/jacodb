import { describe, expect, it } from "vitest";
import { EtsFileDto, MethodDto } from "../src/dto/model";
import { StaticCallExprDto } from "../src/dto/values";
import { lower, lowerProject, methodByName } from "./util";

function staticCalls(method: MethodDto): StaticCallExprDto[] {
    if (method.body === undefined) return [];
    return method.body.cfg.blocks
        .flatMap((block) => block.stmts)
        .flatMap((stmt) => {
            if (stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr") return [stmt.right];
            if (stmt._ === "CallStmt" && stmt.expr._ === "StaticCallExpr") return [stmt.expr];
            return [];
        });
}

function numberIsIntegerCall(method: MethodDto): StaticCallExprDto | undefined {
    return staticCalls(method).find((call) => call.method.name === "isInteger");
}

function hasBuiltinProof(file: EtsFileDto): boolean {
    const calls = file.classes
        .flatMap((clazz) => clazz.methods)
        .flatMap(staticCalls);
    return calls.some((call) => call.builtinProof !== undefined);
}

function expectNoProof(source: string): void {
    expect(hasBuiltinProof(lower(source).file)).toBe(false);
}

describe("verified builtin call proof", () => {
    it("proves Number.isInteger for closed exported scalar arrows and prunes builtin captures", () => {
        const { file } = lower(`
            export const isEven = (num: number): boolean => {
                if (!Number.isInteger(num)) throw new Error("integer expected");
                return num % 2 === 0;
            };
            export const isOdd = (num: number): boolean => {
                if (!Number.isInteger(num)) throw new Error("integer expected");
                return num % 2 !== 0;
            };
            export const isLeapYear = (year: number): boolean => {
                if (year <= 0 || !Number.isInteger(year)) throw new Error("integer expected");
                return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
            };
        `);

        for (const methodName of ["%AM0$%dflt", "%AM1$%dflt", "%AM2$%dflt"]) {
            const method = methodByName(file, methodName);
            const call = numberIsIntegerCall(method);

            expect(method.signature.parameters).toEqual([
                expect.objectContaining({ type: { _: "NumberType" } }),
            ]);
            expect(method.body!.locals.some((local) => local.type._ === "LexicalEnvType")).toBe(false);
            expect(call).toMatchObject({
                _: "StaticCallExpr",
                method: { name: "isInteger", declaringClass: { name: "Number" } },
                builtinProof: {
                    builtin: "NUMBER_IS_INTEGER",
                    entryRequirement: "DIRECT_ISOLATED_ENTRY",
                    entryMethod: method.signature,
                },
            });
        }
    });

    it("keeps proof bound to direct isolated execution when another function calls the entry", () => {
        const { file } = lower(`
            export const isEven = (num: number): boolean => Number.isInteger(num);
            export function caller(num: number): boolean {
                return isEven(num);
            }
        `);
        const entry = methodByName(file, "%AM0$%dflt");
        const call = numberIsIntegerCall(entry)!;

        expect(call.builtinProof?.entryRequirement).toBe("DIRECT_ISOLATED_ENTRY");
        expect(call.builtinProof?.entryMethod).toEqual(entry.signature);
    });

    it("declines shadowed, reassigned, aliased, computed, and effectful contexts", () => {
        const cases = [
            `
                export const f = (
                    Number: { isInteger(value: number): boolean },
                    value: number,
                ): boolean => Number.isInteger(value);
            `,
            `
                export const f = (value: number): boolean => {
                    (Number as any).isInteger = () => true;
                    return Number.isInteger(value);
                };
            `,
            `
                export const f = (value: number): boolean => {
                    Number = {} as NumberConstructor;
                    return Number.isInteger(value);
                };
            `,
            `
                export const f = (value: number): boolean => {
                    const integer = Number.isInteger;
                    return integer(value);
                };
            `,
            `
                export const f = (value: number): boolean => {
                    Number["isInteger"] = () => true;
                    return Number.isInteger(value);
                };
            `,
            `
                function mutate(): void { (Number as any).isInteger = () => true; }
                export const f = (value: number): boolean => {
                    mutate();
                    return Number.isInteger(value);
                };
            `,
            `
                declare function unknown(): void;
                export const f = (value: number): boolean => {
                    unknown();
                    return Number.isInteger(value);
                };
            `,
            `
                export const f = (value: number): boolean => Number.isInteger(++value);
            `,
        ];

        for (const source of cases) expectNoProof(source);
    });

    it("declines foreign ambient reads during module initialization", () => {
        const { file } = lowerProject({
            "ambient.d.ts": "declare var trigger: number;",
            "entry.ts": `
                const setup = trigger;
                export const f = (input: number): boolean => Number.isInteger(input);
            `,
        }, "entry.ts");

        expect(hasBuiltinProof(file)).toBe(false);
    });

    it("declines modules and entries outside the closed direct-entry subset", () => {
        const cases = [
            `
                import { value } from "./dependency";
                export const f = (input: number): boolean => Number.isInteger(input);
            `,
            `
                function sideEffect(): void {}
                sideEffect();
                export const f = (input: number): boolean => Number.isInteger(input);
            `,
            `
                class Holder {}
                export const f = (input: number): boolean => Number.isInteger(input);
            `,
            `const f = (input: number): boolean => Number.isInteger(input);`,
            `
                export const outer = (input: number): boolean => {
                    const nested = (): boolean => Number.isInteger(input);
                    return nested();
                };
            `,
            `
                export const f = (input: number): boolean => {
                    if (Number.isInteger(input)) {
                        const makeError = (): Error => new Error("later");
                        return makeError() === undefined;
                    }
                    return false;
                };
            `,
            `
                export const f = (input: number): boolean => {
                    const object = { input };
                    return Number.isInteger(object.input);
                };
            `,
        ];

        for (const source of cases) expectNoProof(source);
    });

    it("preserves ordinary Number.isInteger lowering when proof is unavailable", () => {
        const { file } = lower(`const f = (input: number): boolean => Number.isInteger(input);`);
        const method = methodByName(file, "%AM0$%dflt");

        expect(numberIsIntegerCall(method)).toBeUndefined();
        expect(method.signature.parameters[0].type._).toBe("LexicalEnvType");
    });
});
