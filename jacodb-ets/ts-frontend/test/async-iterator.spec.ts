import { describe, expect, it } from "vitest";
import { MethodDto } from "../src/dto/model";
import { ValueDto } from "../src/dto/values";
import { serializeEtsFile } from "../src/serialize";
import { validateEtsFile } from "../src/validate";
import { lower, methodByName } from "./util";

/** Executes only the protocol nodes used here. Only AwaitExpr assimilates a Promise;
 * ordinary property reads and calls retain their raw values. This is a concrete DTO
 * regression oracle, not a USVM execution test.
 */
async function execute(method: MethodDto, input: unknown): Promise<unknown> {
    const body = method.body!;
    const locals = new Map<string, any>();
    const read = (value: ValueDto): any => {
        switch (value._) {
            case "Local": return locals.get(value.name);
            case "ParameterRef": return input;
            case "ThisRef": return undefined;
            case "Constant":
                switch (value.type._) {
                    case "UndefinedType": return undefined;
                    case "NullType": return null;
                    case "NumberType": return Number(value.value);
                    case "BooleanType": return value.value === "true";
                    default: return value.value;
                }
            case "StaticFieldRef":
                if (value.field.declaringClass.name !== "Symbol") throw new Error("Unexpected static field");
                return (Symbol as any)[value.field.name];
            case "InstanceFieldRef": return read(value.instance)[value.field.name];
            case "PropertyRef": return read(value.instance)[read(value.key)];
            case "TypeOfExpr": return typeof read(value.arg);
            case "UnopExpr":
                if (value.op !== "!") throw new Error(`Unexpected unary op ${value.op}`);
                return !read(value.arg);
            case "BinopExpr":
            case "ConditionExpr": {
                const left = read(value.left);
                const right = read(value.right);
                switch (value.op) {
                    case "+": return left + right;
                    case "==": return left == right;
                    case "===": return left === right;
                    case "!==": return left !== right;
                    default: throw new Error(`Unexpected binary op ${value.op}`);
                }
            }
            case "PtrCallExpr": return Reflect.apply(read(value.ptr), read(value.receiver!), value.args.map(read));
            case "NewExpr":
                if (value.classType._ !== "ClassType") throw new Error("Unexpected allocation type");
                return { constructorName: value.classType.signature.name };
            case "InstanceCallExpr": {
                const instance = read(value.instance);
                if (value.method.name === "constructor") {
                    const constructor = (globalThis as any)[instance.constructorName];
                    return Reflect.construct(constructor, value.args.map(read));
                }
                const key = value.method.name === "Symbol.asyncIterator" ? Symbol.asyncIterator
                    : value.method.name === "Symbol.iterator" ? Symbol.iterator : value.method.name;
                return Reflect.apply(instance[key], instance, value.args.map(read));
            }
            default: throw new Error(`Unexpected protocol value ${value._}`);
        }
    };

    let blockId = 0;
    for (let steps = 0; steps < 10_000; steps++) {
        const block = body.cfg.blocks[blockId];
        let next = block.successors[0];
        for (const statement of block.stmts) {
            switch (statement._) {
                case "AssignStmt":
                    if (statement.left._ !== "Local") throw new Error("Unexpected assignment target");
                    locals.set(statement.left.name, statement.right._ === "AwaitExpr"
                        ? await read(statement.right.arg) : read(statement.right));
                    break;
                case "IfStmt": next = block.successors[read(statement.condition) ? 1 : 0]; break;
                case "ReturnStmt": return read(statement.arg);
                case "ReturnVoidStmt": return undefined;
                case "ThrowStmt": throw read(statement.arg);
                case "NopStmt": break;
                default: throw new Error(`Unexpected protocol statement ${statement._}`);
            }
        }
        if (next === undefined) throw new Error("Missing successor");
        blockId = next;
    }
    throw new Error("Protocol oracle step limit");
}

function program(source: string) {
    const { file, diagnostics } = lower(source);
    const roundTripped = JSON.parse(serializeEtsFile(file));
    expect(diagnostics.messages).toEqual([]);
    expect(validateEtsFile(roundTripped)).toEqual([]);

    const method = methodByName(roundTripped, "run");
    return {
        ir: (input: unknown) => execute(method, input),
        native: new Function("input", `${source}; return run(input);`) as (input: unknown) => Promise<unknown>,
    };
}

const sumSource = `
    async function run(input) {
        let total = 0;
        for await (const value of input) total += value;
        return total;
    }
`;

describe("for-await iterator acquisition and Async-from-Sync values", () => {
    it("snapshots the iterable before an async getter reassigns its source binding", async () => {
        const iteratorMethod = `function() {
            const value = this.id;
            return { next: function() { return { value, done: false }; } };
        }`;
        for (const asyncMethod of [iteratorMethod, "null"]) {
            const source = `
                async function run() {
                    let input;
                    input = {
                        id: 3,
                        get [Symbol.asyncIterator]() { input = { id: 9 }; return ${asyncMethod}; },
                        get [Symbol.iterator]() { return ${iteratorMethod}; },
                    };
                    for await (const value of input) return value;
                    return 0;
                }
            `;
            const { file, diagnostics } = lower(source);
            const roundTripped = JSON.parse(serializeEtsFile(file));
            const statements = methodByName(roundTripped, "run").body!.cfg.blocks.flatMap((block) => block.stmts);
            const snapshotIndex = statements.findIndex((statement) => statement._ === "AssignStmt"
                && statement.right._ === "Local" && statement.right.name === "input");
            const snapshot = statements[snapshotIndex];
            const lookups = statements.filter((statement) => statement._ === "AssignStmt"
                && statement.right._ === "PropertyRef");
            const calls = statements.filter((statement) => statement._ === "AssignStmt"
                && statement.right._ === "PtrCallExpr" && statement.right.method.name.startsWith("Symbol."));

            expect(diagnostics.messages).toEqual([]);
            expect(validateEtsFile(roundTripped)).toEqual([]);
            expect(snapshot).toMatchObject({ _: "AssignStmt", left: { _: "Local" }, right: { _: "Local", name: "input" } });
            expect(snapshot.left.name).not.toBe("input");
            expect(lookups).toHaveLength(2);
            expect(calls).toHaveLength(2);
            expect(lookups.every((statement) => statement.right.instance.name === snapshot.left.name)).toBe(true);
            expect(calls.every((statement) => statement.right.receiver?.name === snapshot.left.name)).toBe(true);
            expect(lookups.every((statement) => statements.indexOf(statement) > snapshotIndex)).toBe(true);
            expect(await new Function(`${source}; return run();`)()).toBe(3);
        }
    });

    it("accepts arrays and synchronous generators yielding Promises", async () => {
        const run = program(sumSource);
        const inputs = [
            () => [3, 4],
            () => (function* () { yield Promise.resolve(3); yield Promise.resolve(4); })(),
        ];

        for (const input of inputs) {
            expect(await run.ir(input())).toBe(await run.native(input()));
            expect(await run.ir(input())).toBe(7);
        }
    });

    it("selects the async method and does not additionally await an async iterator's value", async () => {
        const run = program(`
            async function run(input) {
                let kind = "empty";
                for await (const value of input) kind = typeof value;
                return kind;
            }
        `);
        const input = () => {
            let step = 0;
            return {
                [Symbol.asyncIterator]() {
                    return { next: () => Promise.resolve(step++ === 0
                        ? { value: Promise.resolve(7), done: false } : { done: true }) };
                },
                [Symbol.iterator]() { throw new Error("sync fallback must not run"); },
            };
        };

        expect(await run.ir(input())).toBe("object");
        expect(await run.ir(input())).toBe(await run.native(input()));
    });

    it("falls back for null and undefined, but rejects a present non-callable async method", async () => {
        const run = program(sumSource);
        for (const asyncMethod of [null, undefined]) {
            const input = () => ({
                [Symbol.asyncIterator]: asyncMethod,
                *[Symbol.iterator]() { yield 7; },
            });

            expect(await run.ir(input())).toBe(await run.native(input()));
        }

        let syncReads = 0;
        const invalid = {
            [Symbol.asyncIterator]: 1,
            get [Symbol.iterator]() { syncReads++; return [][Symbol.iterator]; },
        };

        await expect(run.ir(invalid)).rejects.toBeInstanceOf(TypeError);
        await expect(run.native(invalid)).rejects.toBeInstanceOf(TypeError);
        expect(syncReads).toBe(0);
    });

    it("caches next, reads done before value and awaits the final sync value without awaiting its result", async () => {
        const run = program(sumSource);
        const input = (events: string[]) => {
            let step = 0;
            const iterator = {
                get next() {
                    events.push("next lookup");
                    return function () {
                        expect(this).toBe(iterator);
                        const index = step++;
                        return {
                            get done() { events.push(`done ${index}`); return index === 0 ? "" : "finished"; },
                            get value() {
                                events.push(`value ${index}`);
                                return { then(resolve: (value: number) => void) {
                                    events.push(`await ${index}`);
                                    resolve(index === 0 ? 7 : 0);
                                } };
                            },
                            then() { throw new Error("must not await the IteratorResult"); },
                        };
                    };
                },
            };
            return { [Symbol.iterator]() { return iterator; } };
        };
        const irEvents: string[] = [];
        const nativeEvents: string[] = [];

        expect(await run.ir(input(irEvents))).toBe(7);
        expect(await run.native(input(nativeEvents))).toBe(7);
        expect(irEvents).toEqual(nativeEvents);
        expect(irEvents).toEqual(["next lookup", "done 0", "value 0", "await 0", "done 1", "value 1", "await 1"]);
    });

    it("propagates getter failures and rejects primitive iterators and results", async () => {
        const run = program(sumSource);
        const failure = new Error("async lookup failed");
        const failingLookup = {
            get [Symbol.asyncIterator]() { throw failure; },
            [Symbol.iterator]() { throw new Error("must not select fallback"); },
        };

        await expect(run.ir(failingLookup)).rejects.toBe(failure);
        await expect(run.native(failingLookup)).rejects.toBe(failure);

        for (const key of [Symbol.iterator, Symbol.asyncIterator]) {
            await expect(run.ir({ [key]: () => 1 })).rejects.toBeInstanceOf(TypeError);
            await expect(run.native({ [key]: () => 1 })).rejects.toBeInstanceOf(TypeError);
            await expect(run.ir({ [key]: () => ({ next: () => 1 }) })).rejects.toBeInstanceOf(TypeError);
            await expect(run.native({ [key]: () => ({ next: () => 1 }) })).rejects.toBeInstanceOf(TypeError);
        }
    });

    it("propagates rejection of a Promise yielded by a synchronous iterator", async () => {
        const run = program(sumSource);
        const failure = new Error("yielded value rejected");
        const input = () => (function* () { yield Promise.reject(failure); })();

        await expect(run.ir(input())).rejects.toBe(failure);
        await expect(run.native(input())).rejects.toBe(failure);
    });
});
