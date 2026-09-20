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

function builtinCall(method: MethodDto, name: string): StaticCallExprDto | undefined {
    return staticCalls(method).find((call) => call.method.name === name);
}

function numberIsIntegerCall(method: MethodDto): StaticCallExprDto | undefined {
    return builtinCall(method, "isInteger");
}

function methodWithBodyByName(file: EtsFileDto, name: string): MethodDto {
    const method = file.classes
        .flatMap((clazz) => clazz.methods)
        .find((candidate) => candidate.signature.name === name && candidate.body !== undefined);
    if (method === undefined) throw new Error("method '" + name + "' with body not found");

    return method;
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

function expectNoAmbientProjectProof(entrySource: string): void {
    const { file } = lowerProject({
        "ambient.d.ts": "declare var trigger: number;",
        "entry.ts": entrySource,
    }, "entry.ts");

    expect(hasBuiltinProof(file)).toBe(false);
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

    it("proves the pinned Math.abs loop with a numeric default and scalar backedge", () => {
        const { file } = lower(`
            export const squareRoot = (num: number, precision: number = 1e-15): number => {
                if (num < 0) throw new Error("number must be non-negative number");
                if (num === 0) return 0;

                let sqrt: number = num;
                let curr: number;
                while (true) {
                    curr = 0.5 * (sqrt + num / sqrt);
                    if (Math.abs(curr - sqrt) < precision) {
                        return sqrt;
                    }
                    sqrt = curr;
                }
            };
        `);
        const method = methodByName(file, "%AM0$%dflt");
        const call = builtinCall(method, "abs");

        expect(method.body!.locals.some((local) => local.type._ === "LexicalEnvType")).toBe(false);
        expect(call).toMatchObject({
            method: { declaringClass: { name: "Math" } },
            builtinProof: {
                builtin: "MATH_ABS",
                entryRequirement: "DIRECT_ISOLATED_ENTRY",
                entryMethod: method.signature,
            },
        });
    });

    it("proves Number.isInteger in the pinned overloaded range declaration", () => {
        const { file } = lower(`
            export function range(end: number): number[];
            export function range(start: number, end: number): number[];
            export function range(start: number, end: number, step: number): number[];
            export function range(start: number, end?: number, step = 1): number[] {
                if (end == null) {
                    end = start;
                    start = 0;
                }

                if (!Number.isInteger(step) || step === 0) {
                    throw new Error("The step value must be a non-zero integer.");
                }

                const length = Math.max(Math.ceil((end - start) / step), 0);
                const result = new Array<number>(length);
                for (let i = 0; i < length; i++) {
                    result[i] = start + i * step;
                }
                return result;
            }
        `);
        const method = methodWithBodyByName(file, "range");

        expect(numberIsIntegerCall(method)).toMatchObject({
            method: { declaringClass: { name: "Number" } },
            builtinProof: {
                builtin: "NUMBER_IS_INTEGER",
                entryRequirement: "DIRECT_ISOLATED_ENTRY",
                entryMethod: method.signature,
            },
        });
        expect(builtinCall(method, "max")?.builtinProof).toBeUndefined();
    });

    it("proves pinned Math.min and Math.max assignments in generic array entries", () => {
        const minFile = lower(`
            export function dropRight<T>(arr: readonly T[], itemsCount: number): T[] {
                itemsCount = Math.min(-itemsCount, 0);
                if (itemsCount === 0) {
                    return arr.slice();
                }
                return arr.slice(0, itemsCount);
            }
        `).file;
        const maxFile = lower(`
            export function drop<T>(arr: readonly T[], itemsCount: number): T[] {
                itemsCount = Math.max(itemsCount, 0);
                return arr.slice(itemsCount);
            }
        `).file;
        const minMethod = methodWithBodyByName(minFile, "dropRight");
        const maxMethod = methodWithBodyByName(maxFile, "drop");

        expect(builtinCall(minMethod, "min")).toMatchObject({
            method: { declaringClass: { name: "Math" } },
            builtinProof: {
                builtin: "MATH_MIN",
                entryMethod: minMethod.signature,
            },
        });
        expect(builtinCall(maxMethod, "max")).toMatchObject({
            method: { declaringClass: { name: "Math" } },
            builtinProof: {
                builtin: "MATH_MAX",
                entryMethod: maxMethod.signature,
            },
        });
    });

    it("admits only entry locals and verified module scalars in evaluated prefixes", () => {
        const { file } = lower(`
            const offset = 1;
            export function named(value: number): number {
                const adjusted = value + offset;
                return Math.abs(adjusted);
            }
            export const arrow = (value: number): number => {
                const adjusted = value + offset;
                return Math.abs(adjusted);
            };
        `);
        const named = methodWithBodyByName(file, "named");
        const arrow = methodByName(file, "%AM0$%dflt");

        for (const method of [named, arrow]) {
            expect(builtinCall(method, "abs")).toMatchObject({
                method: { declaringClass: { name: "Math" } },
                builtinProof: {
                    builtin: "MATH_ABS",
                    entryMethod: method.signature,
                },
            });
        }
    });

    it("proves optional-parameter guards against intrinsic undefined for declarations and arrows", () => {
        const { file } = lower(`
            export function named(value?: number): number {
                if (value === undefined) value = -1;
                return Math.abs(value);
            }
            export const arrow = (value?: number): number => {
                if (value === undefined) value = -1;
                return Math.abs(value);
            };
        `);
        const named = methodWithBodyByName(file, "named");
        const arrow = methodByName(file, "%AM0$%dflt");

        for (const method of [named, arrow]) {
            expect(builtinCall(method, "abs")).toMatchObject({
                method: { declaringClass: { name: "Math" } },
                builtinProof: {
                    builtin: "MATH_ABS",
                    entryMethod: method.signature,
                },
            });
        }
    });

    it("declines a project-global shadow named undefined", () => {
        const { file } = lowerProject({
            "globals.ts": "declare var undefined: undefined;",
            "entry.ts": `
                export function f(value?: number): number {
                    if (value === undefined) value = -1;
                    return Math.abs(value);
                }
            `,
        }, "entry.ts");

        expect(hasBuiltinProof(file)).toBe(false);
    });

    it("declines ambient scalar reads for declarations and arrows at every evaluated site", () => {
        const entryPairs = [
            [
                `
                    export function f(value: number): number {
                        const ignored = trigger;
                        return Math.abs(value);
                    }
                `,
                `
                    export const f = (value: number): number => {
                        const ignored = trigger;
                        return Math.abs(value);
                    };
                `,
            ],
            [
                `
                    export function f(value: number): number {
                        return Math.abs(value + trigger);
                    }
                `,
                `
                    export const f = (value: number): number => Math.abs(value + trigger);
                `,
            ],
            [
                `
                    export function f(value: number): number {
                        let current = value;
                        while (true) {
                            if (Math.abs(current) < 1) return current;
                            current = trigger;
                        }
                    }
                `,
                `
                    export const f = (value: number): number => {
                        let current = value;
                        while (true) {
                            if (Math.abs(current) < 1) return current;
                            current = trigger;
                        }
                    };
                `,
            ],
        ];

        for (const [declaration, arrow] of entryPairs) {
            expectNoAmbientProjectProof(declaration);
            expectNoAmbientProjectProof(arrow);
        }
    });

    it("declines unsafe numeric builtin prefixes and identities", () => {
        const cases = [
            `
                export const f = (Math: { abs(value: number): number }, value: number): number =>
                    Math.abs(value);
            `,
            `
                export const f = (value: number): number => {
                    (Math as any).abs = () => 0;
                    return Math.abs(value);
                };
            `,
            `
                declare function unknown(): void;
                export const f = (value: number): number => {
                    unknown();
                    return Math.abs(value);
                };
            `,
            `
                declare function unknown(): number;
                export const f = (value: number = unknown()): number => Math.abs(value);
            `,
            `
                export const f = (object: { value: number }): number => Math.abs(object.value);
            `,
            `
                export const f = (value: number): number => Math.min(value);
            `,
            `
                export const f = (value: number): number => Math.abs(value, value);
            `,
            `
                export const f = (value: number): number => Math.max(value, value, value);
            `,
            `
                export const f = (value: number): boolean => Number.isInteger(value, value);
            `,
            `
                declare function unknown(): void;
                export const f = (value: number): number => {
                    let current = value;
                    while (true) {
                        if (Math.abs(current) < 1) return current;
                        unknown();
                        current = current / 2;
                    }
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
