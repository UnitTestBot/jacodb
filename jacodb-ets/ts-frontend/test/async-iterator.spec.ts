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
    let caught: unknown;
    const read = (value: ValueDto): any => {
        switch (value._) {
            case "Local": return locals.get(value.name);
            case "ParameterRef": return input;
            case "ThisRef": return undefined;
            case "CaughtExceptionRef": return caught;
            case "RequireObjectCoercibleExpr": {
                const source = read(value.arg);
                if (source === null || source === undefined) throw new TypeError("nullish binding");
                return source;
            }
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
        for (let index = 0; index < block.stmts.length; index++) {
            const statement = block.stmts[index];
            try {
                switch (statement._) {
                    case "AssignStmt":
                        if (statement.left._ !== "Local") throw new Error("Unexpected assignment target");
                        locals.set(statement.left.name, statement.right._ === "AwaitExpr"
                            ? await read(statement.right.arg) : read(statement.right));
                        break;
                    case "CallStmt": read(statement.expr); break;
                    case "IfStmt": next = block.successors[read(statement.condition) ? 1 : 0]; break;
                    case "ReturnStmt": return read(statement.arg);
                    case "ReturnVoidStmt": return undefined;
                    case "ThrowStmt": throw read(statement.arg);
                    case "NopStmt": break;
                    default: throw new Error(`Unexpected protocol statement ${statement._}`);
                }
            } catch (error) {
                const handler = block.exceptionalSuccessors?.find((edge) => edge.stmtIndex === index);
                if (handler === undefined) throw error;
                caught = error;
                next = handler.target;
                break;
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


const breakSource = "async function run(input) { for await (const value of input) break; return 9; }";

function closingInput(events: string[], options: {
    sync?: boolean;
    value?: unknown;
    close?: () => unknown;
    getterFailure?: unknown;
} = {}) {
    const iterator = {
        next: () => ({ value: Object.prototype.hasOwnProperty.call(options, "value") ? options.value : 1, done: false }),
        get return() {
            events.push("return lookup");
            if (options.getterFailure !== undefined) throw options.getterFailure;
            return function () {
                expect(this).toBe(iterator);
                events.push("return call");
                return options.close === undefined ? { done: true } : options.close();
            };
        },
    };
    return { [options.sync ? Symbol.iterator : Symbol.asyncIterator]: () => iterator };
}

describe("for-await abrupt completion closes its iterator", () => {
    for (const completion of ["break;", "return value;", "throw value;"]) {
        it(`awaits async iterator cleanup for ${completion}`, async () => {
            const run = program(`async function run(input) { for await (const value of input) { ${completion} } return 9; }`);
            const failure = new Error("body failure");
            const outcomes: unknown[] = [];
            const logs: string[][] = [];

            for (const consume of [run.native, run.ir]) {
                const events: string[] = [];
                const input = closingInput(events, {
                    value: completion.startsWith("throw") ? failure : 7,
                    close: () => ({ then(resolve: (result: object) => void) {
                        events.push("return awaited");
                        resolve({ done: true });
                    } }),
                });

                try { outcomes.push(await consume(input)); } catch (error) { outcomes.push(error); }
                logs.push(events);
            }

            expect(outcomes[1]).toBe(outcomes[0]);
            expect(logs[1]).toEqual(logs[0]);
            expect(logs[1]).toEqual(["return lookup", "return call", "return awaited"]);
        });
    }

    it("closes on a runtime body failure and a binding initialization failure", async () => {
        const failure = new Error("body call failure");
        const cases = [
            { source: "async function run(input) { for await (const value of input) value.fail(); }", value: { fail() { throw failure; } } },
            { source: "async function run(input) { for await (const {field} of input) return field; }", value: null },
        ];

        for (const scenario of cases) {
            const run = program(scenario.source);
            for (const consume of [run.native, run.ir]) {
                const events: string[] = [];
                const input = closingInput(events, { value: scenario.value });

                if (scenario.value === null) await expect(consume(input)).rejects.toBeInstanceOf(TypeError);
                else await expect(consume(input)).rejects.toBe(failure);
                expect(events).toEqual(["return lookup", "return call"]);
            }
        }
    });

    it("preserves a body throw over return getter, call, await and result failures", async () => {
        const bodyFailure = new Error("body failure");
        const closeFailure = new Error("close failure");
        const run = program("async function run(input) { for await (const value of input) throw value; }");
        const closes = [
            { getterFailure: closeFailure },
            { close: () => { throw closeFailure; } },
            { close: () => Promise.reject(closeFailure) },
            { close: () => 1 },
        ];

        for (const options of closes) {
            for (const consume of [run.native, run.ir]) {
                await expect(consume(closingInput([], { ...options, value: bodyFailure }))).rejects.toBe(bodyFailure);
            }
        }
    });

    it("propagates cleanup failures for break and return", async () => {
        const closeFailure = new Error("close failure");
        for (const completion of ["break;", "return value;"]) {
            const run = program(`async function run(input) { for await (const value of input) { ${completion} } }`);
            for (const consume of [run.native, run.ir]) {
                await expect(consume(closingInput([], { getterFailure: closeFailure }))).rejects.toBe(closeFailure);
                await expect(consume(closingInput([], { close: () => Promise.reject(closeFailure) }))).rejects.toBe(closeFailure);
                await expect(consume(closingInput([], { close: () => 1 }))).rejects.toBeInstanceOf(TypeError);
            }
        }
    });

    it("closes the sync fallback without assimilating its IteratorResult", async () => {
        const run = program(breakSource);
        const logs: string[][] = [];
        for (const consume of [run.native, run.ir]) {
            const events: string[] = [];
            const input = closingInput(events, { sync: true, close: () => ({
                get done() { events.push("close done"); return true; },
                get value() { events.push("close value"); return { then(resolve: (value: number) => void) {
                    events.push("close value awaited"); resolve(0);
                } }; },
                then() { throw new Error("must not await sync IteratorResult"); },
            }) });

            expect(await consume(input)).toBe(9);
            logs.push(events);
        }

        expect(logs[1]).toEqual(logs[0]);
        expect(logs[1]).toEqual(["return lookup", "return call", "close done", "close value", "close value awaited"]);
    });

    it("does not close on exhaustion, same-loop continue or next rejection", async () => {
        const run = program("async function run(input) { for await (const value of input) continue; return 9; }");
        const failure = new Error("next failure");
        for (const rejectNext of [false, true]) {
            for (const consume of [run.native, run.ir]) {
                const events: string[] = [];
                let step = 0;
                const input = closingInput(events);
                input[Symbol.asyncIterator]().next = () => {
                    if (rejectNext) throw failure;
                    return { value: 1, done: step++ > 0 };
                };

                if (rejectNext) await expect(consume(input)).rejects.toBe(failure);
                else expect(await consume(input)).toBe(9);
                expect(events).toEqual([]);
            }
        }
    });

    it("runs inner finally before iterator cleanup and outer finally after cleanup", async () => {
        const run = program(`async function run(input) {
            try { for await (const value of input) {
                try { return value; } finally { input.inner(); }
            } } finally { input.outer(); }
        }`);
        const logs: string[][] = [];
        for (const consume of [run.native, run.ir]) {
            const events: string[] = [];
            const input = Object.assign(closingInput(events), {
                inner: () => events.push("inner finally"),
                outer: () => events.push("outer finally"),
            });

            expect(await consume(input)).toBe(1);
            logs.push(events);
        }

        expect(logs[1]).toEqual(logs[0]);
        expect(logs[1]).toEqual(["inner finally", "return lookup", "return call", "outer finally"]);
    });

    it("closes nested iterators in order on a labeled outer continue", async () => {
        const run = program(`async function run(input) {
            outer: for await (const value of input) {
                for await (const other of input) continue outer;
            }
            return 9;
        }`);
        const logs: string[][] = [];
        for (const consume of [run.native, run.ir]) {
            const events: string[] = [];
            let acquired = 0;
            const input = { [Symbol.asyncIterator]() {
                const id = acquired++;
                let step = 0;
                return {
                    next: () => ({ value: 1, done: id === 0 && step++ > 0 }),
                    return() { events.push(`close ${id}`); return { done: true }; },
                };
            } };

            expect(await consume(input)).toBe(9);
            logs.push(events);
        }

        expect(logs[1]).toEqual(logs[0]);
        expect(logs[1]).toEqual(["close 1"]);
    });

    it("closes both iterators from inner to outer on a labeled break", async () => {
        const run = program(`async function run(input) {
            outer: for await (const value of input) {
                for await (const other of input) break outer;
            }
            return 9;
        }`);
        for (const consume of [run.native, run.ir]) {
            const events: number[] = [];
            let acquired = 0;
            const input = { [Symbol.asyncIterator]() {
                const id = acquired++;
                return {
                    next: () => ({ value: 1, done: false }),
                    return() { events.push(id); return { done: true }; },
                };
            } };

            expect(await consume(input)).toBe(9);
            expect(events).toEqual([1, 0]);
        }
    });

    it("routes a cleanup failure to the enclosing catch without closing twice", async () => {
        const run = program(`async function run(input) {
            try { for await (const value of input) break; }
            catch (failure) { return failure; }
        }`);
        const failure = new Error("close failure");
        for (const consume of [run.native, run.ir]) {
            const events: string[] = [];
            const input = closingInput(events, { close: () => { throw failure; } });

            expect(await consume(input)).toBe(failure);
            expect(events).toEqual(["return lookup", "return call"]);
        }
    });

    it("captures the return expression before cleanup changes its source", async () => {
        const run = program("async function run(input) { for await (const value of input) return input.marker; }");
        for (const consume of [run.native, run.ir]) {
            const input = Object.assign(closingInput([], { close: () => {
                input.marker = 2;
                return { done: true };
            } }), { marker: 1 });

            expect(await consume(input)).toBe(1);
            expect(input.marker).toBe(2);
        }
    });

    it("accepts a missing return method on async and synchronous iterators", async () => {
        const run = program(breakSource);
        for (const key of [Symbol.iterator, Symbol.asyncIterator]) {
            for (const consume of [run.native, run.ir]) {
                expect(await consume({ [key]: () => ({ next: () => ({ value: 1, done: false }) }) })).toBe(9);
            }
        }
    });

    it("does not close when reading the async iterator value fails before binding", async () => {
        const run = program(breakSource);
        const failure = new Error("value getter failure");
        for (const consume of [run.native, run.ir]) {
            let closes = 0;
            const input = { [Symbol.asyncIterator]: () => ({
                next: () => ({ done: false, get value() { throw failure; } }),
                return() { closes++; return { done: true }; },
            }) };

            await expect(consume(input)).rejects.toBe(failure);
            expect(closes).toBe(0);
        }
    });

});
