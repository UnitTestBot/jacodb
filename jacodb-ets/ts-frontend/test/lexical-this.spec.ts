import { describe, expect, it } from "vitest";
import * as ts from "typescript";
import { serializeEtsFile } from "../src/serialize";
import { executeObjectIr } from "./object-runtime";
import { lower, methodByName } from "./util";

describe("lexical arrow receivers", () => {
    it("uses the captured receiver before evaluating an arrow parameter default", () => {
        const source = `
            export function result(): number {
                const source = {
                    offset: 11,
                    install: function(target: any) { target.callback = (value = this.offset) => value + 1; },
                };
                const target: any = { offset: 100 };
                source.install(target);
                return target.callback();
            }
        `;
        const { file, diagnostics } = lower(source);
        const roundTripped = JSON.parse(serializeEtsFile(file));

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(roundTripped, "result")).toBe(12);

        const javascript = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        expect(new Function("exports", `${javascript}\nreturn exports.result();`)({})).toBe(12);
    });

    it("preserves an outer binding referenced only by a parameter default", () => {
        const { file, diagnostics } = lower(`
            function make(seed: number) { return (value = seed) => value; }
            export function result() { return make(12)(); }
        `);
        const roundTripped = JSON.parse(serializeEtsFile(file));

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(roundTripped, "result")).toBe(12);
    });

    it("preserves the creation receiver when an arrow is stored on another object", () => {
        const source = `
            class Box {
                callback!: (value: number) => number;
                constructor(public offset: number) {}
                installOn(target: Box): void {
                    target.callback = (value: number) => this.offset + value;
                }
                installOrdinaryOn(target: Box): void {
                    target.callback = function(value: number) { return this.offset + value; };
                }
                call(value: number): number { return this.callback(value); }
            }
            export function results(): number[] {
                const source = new Box(11);
                const target = new Box(100);
                source.installOn(target);
                const arrowResult = target.call(1);
                source.installOrdinaryOn(target);
                return [arrowResult, target.call(1)];
            }
        `;
        const { file, diagnostics } = lower(source);
        const roundTripped = JSON.parse(serializeEtsFile(file));
        const box = roundTripped.classes.find((classDto) => classDto.signature.name === "Box");
        const install = box.methods.find((method) => method.signature.name === "installOn");
        const arrowValue = install.body.locals.find((local) => local.name.startsWith("%AM"));
        const arrow = methodByName(roundTripped, arrowValue.name);
        const ordinary = box.methods.find((method) => method.signature.name.includes("$installOrdinaryOn"));

        expect(diagnostics.messages).toEqual([]);
        expect(arrowValue.type.isArrow).toBe(true);
        expect(arrow.signature.parameters[0].type).toMatchObject({
            _: "LexicalEnvType", closures: [{ name: "this", type: { _: "ClassType" } }],
        });
        expect(arrow.body.cfg.blocks.flatMap((block) => block.stmts)).toContainEqual(expect.objectContaining({
            _: "AssignStmt", left: expect.objectContaining({ name: "this" }),
            right: expect.objectContaining({ _: "ClosureFieldRef", fieldName: "this" }),
        }));
        expect(ordinary.body.cfg.blocks.flatMap((block) => block.stmts)).toContainEqual(expect.objectContaining({
            _: "AssignStmt", left: expect.objectContaining({ name: "this" }),
            right: expect.objectContaining({ _: "ThisRef" }),
        }));

        const javascript = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const concrete = new Function("exports", `${javascript}\nreturn exports.results();`)({});
        expect(concrete).toEqual([12, 101]);
    });

    it("forwards lexical this through nested arrows and stops at ordinary functions", () => {
        const { file, diagnostics } = lower(`
            class Box {
                offset = 11;
                nested() { return () => () => this.offset; }
                ordinary() { return () => function() { return this.offset; }; }
            }
        `);
        const methods = file.classes.flatMap((classDto) => classDto.methods)
            .filter((method) => method.signature.name.startsWith("%AM"));
        const nested = methods.filter((method) => method.signature.name.includes("$nested"));
        const ordinary = methods.filter((method) => method.signature.name.includes("$ordinary"));

        expect(diagnostics.messages).toEqual([]);
        expect(nested).toHaveLength(2);
        expect(nested.every((method) => method.signature.parameters[0].type._ === "LexicalEnvType")).toBe(true);
        expect(ordinary.every((method) => method.signature.parameters.length === 0)).toBe(true);
    });
    it("uses the creation receiver when a stored arrow body reads this", () => {
        const { file, diagnostics } = lower(`
            export function result() {
                const source = {
                    offset: 11,
                    install: function(target: any) { target.callback = () => this.offset + 1; },
                };
                const target: any = { offset: 100 };
                source.install(target);
                return target.callback();
            }
        `);
        const roundTripped = JSON.parse(serializeEtsFile(file));

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(roundTripped, "result")).toBe(12);
    });

});
