import { describe, expect, it } from "vitest";
import { EtsFileDto, BasicBlockDto, BodyDto } from "../src/dto/model";
import { StmtDto } from "../src/dto/stmts";
import { UNKNOWN_TYPE, NUMBER_TYPE } from "../src/dto/types";
import { LocalDto, ValueDto } from "../src/dto/values";
import { validateEtsFile } from "../src/validate";

const FILE_SIG = { projectName: "p", fileName: "f.ts" };
const CLASS_SIG = { name: "C", declaringFile: FILE_SIG };
const METHOD_SIG = {
    declaringClass: CLASS_SIG,
    name: "m",
    parameters: [],
    returnType: { _: "VoidType" } as const,
};

function local(name: string): LocalDto {
    return { _: "Local", name, type: UNKNOWN_TYPE };
}

function fileWithBody(body: BodyDto): EtsFileDto {
    return {
        signature: FILE_SIG,
        namespaces: [],
        classes: [
            {
                signature: CLASS_SIG,
                modifiers: 0,
                decorators: [],
                superClassName: "",
                implementedInterfaceNames: [],
                fields: [],
                methods: [{ signature: METHOD_SIG, modifiers: 0, decorators: [], body }],
            },
        ],
        importInfos: [],
        exportInfos: [],
    };
}

function bodyWithBlocks(blocks: BasicBlockDto[], locals: string[] = []): BodyDto {
    return {
        locals: locals.map((name) => ({ name, type: UNKNOWN_TYPE })),
        cfg: { blocks },
    };
}

function violations(body: BodyDto): string[] {
    return validateEtsFile(fileWithBody(body));
}

describe("validateEtsFile", () => {
    it("accepts an empty cfg", () => {
        expect(violations(bodyWithBlocks([]))).toEqual([]);
    });

    it("rejects block ids not equal to their index", () => {
        const errs = violations(
            bodyWithBlocks([{ id: 1, successors: [], predecessors: [], stmts: [{ _: "ReturnVoidStmt" }] }]),
        );
        expect(errs.some((e) => e.includes("id must equal its index"))).toBe(true);
    });

    it("rejects out-of-range successors", () => {
        const errs = violations(
            bodyWithBlocks([{ id: 0, successors: [5], predecessors: [], stmts: [{ _: "NopStmt" }] }]),
        );
        expect(errs.some((e) => e.includes("successor 5 out of range"))).toBe(true);
    });

    it("requires exactly 2 successors for an IfStmt block", () => {
        const ifStmt: StmtDto = {
            _: "IfStmt",
            condition: {
                _: "ConditionExpr",
                op: "==",
                left: local("a"),
                right: { _: "Constant", value: "0", type: NUMBER_TYPE },
                type: { _: "BooleanType" },
            },
        };
        const errs = violations(
            bodyWithBlocks(
                [
                    { id: 0, successors: [1], predecessors: [], stmts: [ifStmt] },
                    { id: 1, successors: [], predecessors: [0], stmts: [{ _: "ReturnVoidStmt" }] },
                ],
                ["a"],
            ),
        );
        expect(errs.some((e) => e.includes("ends with IfStmt but has 1 successors"))).toBe(true);
    });

    it("rejects successors on return blocks", () => {
        const errs = violations(
            bodyWithBlocks([
                { id: 0, successors: [1], predecessors: [], stmts: [{ _: "ReturnVoidStmt" }] },
                { id: 1, successors: [], predecessors: [0], stmts: [{ _: "ReturnVoidStmt" }] },
            ]),
        );
        expect(errs.some((e) => e.includes("ends with 'ReturnVoidStmt' but has 1 successors"))).toBe(true);
    });

    it("rejects more than one successor on a non-branching block", () => {
        const errs = violations(
            bodyWithBlocks([
                { id: 0, successors: [1, 2], predecessors: [], stmts: [{ _: "NopStmt" }] },
                { id: 1, successors: [], predecessors: [0], stmts: [{ _: "ReturnVoidStmt" }] },
                { id: 2, successors: [], predecessors: [0], stmts: [{ _: "ReturnVoidStmt" }] },
            ]),
        );
        expect(errs.some((e) => e.includes("non-branching block has 2 successors"))).toBe(true);
    });

    it("rejects terminators in the middle of a block", () => {
        const errs = violations(
            bodyWithBlocks([
                {
                    id: 0,
                    successors: [],
                    predecessors: [],
                    stmts: [{ _: "ReturnVoidStmt" }, { _: "NopStmt" }],
                },
            ]),
        );
        expect(errs.some((e) => e.includes("terminator 'ReturnVoidStmt' at position 0"))).toBe(true);
    });

    it("rejects invalid source-origin references", () => {
        const body = bodyWithBlocks([
            { id: 0, successors: [], predecessors: [], stmts: [{ _: "ReturnVoidStmt" }] },
        ]);
        body.stmtOrigins = [
            {
                blockId: 0,
                stmtIndex: 3,
                source: {
                    fileName: "f.ts",
                    startOffset: 10,
                    endOffset: 5,
                    startLine: 1,
                    startColumn: 0,
                    endLine: 0,
                    endColumn: 2,
                    nodeKind: "ReturnStatement",
                },
            },
        ];

        const errs = violations(body);
        expect(errs.some((e) => e.includes("stmt 3 outside block 0"))).toBe(true);
        expect(errs.some((e) => e.includes("invalid offset range"))).toBe(true);
        expect(errs.some((e) => e.includes("invalid line/column range"))).toBe(true);
    });

    it("rejects undeclared locals", () => {
        const errs = violations(
            bodyWithBlocks([
                {
                    id: 0,
                    successors: [],
                    predecessors: [],
                    stmts: [{ _: "ReturnStmt", arg: local("ghost") }],
                },
            ]),
        );
        expect(errs.some((e) => e.includes("local 'ghost' is not declared"))).toBe(true);
    });

    it("rejects locals with the reserved _tmp prefix", () => {
        const errs = violations(
            bodyWithBlocks(
                [
                    {
                        id: 0,
                        successors: [],
                        predecessors: [],
                        stmts: [{ _: "ReturnStmt", arg: local("_tmp0") }],
                    },
                ],
                ["_tmp0"],
            ),
        );
        expect(errs.some((e) => e.includes("reserved prefix '_tmp'"))).toBe(true);
    });

    it("rejects non-Local instance in InstanceCallExpr", () => {
        const badCall = {
            _: "InstanceCallExpr",
            instance: { _: "Constant", value: "1", type: NUMBER_TYPE } as unknown as LocalDto,
            method: METHOD_SIG,
            args: [],
        } as const;
        const errs = violations(
            bodyWithBlocks([
                { id: 0, successors: [], predecessors: [], stmts: [{ _: "CallStmt", expr: badCall }, { _: "ReturnVoidStmt" }] },
            ]),
        );
        expect(errs.some((e) => e.includes("InstanceCallExpr.instance"))).toBe(true);
    });

    it("rejects expr-kind ArrayRef index", () => {
        const arrayRef: ValueDto = {
            _: "ArrayRef",
            array: local("arr"),
            index: { _: "BinopExpr", op: "+", left: local("i"), right: local("j") },
            type: UNKNOWN_TYPE,
        };
        const errs = violations(
            bodyWithBlocks(
                [
                    {
                        id: 0,
                        successors: [],
                        predecessors: [],
                        stmts: [{ _: "AssignStmt", left: local("x"), right: arrayRef }, { _: "ReturnVoidStmt" }],
                    },
                ],
                ["arr", "i", "j", "x"],
            ),
        );
        expect(errs.some((e) => e.includes("ArrayRef.index has expr kind"))).toBe(true);
    });

    it("rejects expr-kind PtrCallExpr ptr", () => {
        const badPtrCall = {
            _: "PtrCallExpr",
            ptr: { _: "BinopExpr", op: "+", left: local("a"), right: local("b") },
            method: METHOD_SIG,
            args: [],
        } as const;
        const errs = violations(
            bodyWithBlocks(
                [
                    {
                        id: 0,
                        successors: [],
                        predecessors: [],
                        stmts: [
                            { _: "CallStmt", expr: badPtrCall as never },
                            { _: "ReturnVoidStmt" },
                        ],
                    },
                ],
                ["a", "b"],
            ),
        );
        expect(errs.some((e) => e.includes("PtrCallExpr.ptr has expr kind"))).toBe(true);
    });

    it("rejects raw fallback values outside Local-assignment RHS", () => {
        const rawValue = { _: "SomeExoticValue", type: UNKNOWN_TYPE } as unknown as ValueDto;
        // raw as a call argument — forbidden (Kotlin ensureOneAddress rejects EtsRawEntity)
        const errs = violations(
            bodyWithBlocks([
                {
                    id: 0,
                    successors: [],
                    predecessors: [],
                    stmts: [
                        { _: "CallStmt", expr: { _: "StaticCallExpr", method: METHOD_SIG, args: [rawValue] } },
                        { _: "ReturnVoidStmt" },
                    ],
                },
            ]),
        );
        expect(errs.some((e) => e.includes("raw value") && e.includes("operand position"))).toBe(true);

        // raw as the RHS of a Local assignment — the one allowed position
        const ok = violations(
            bodyWithBlocks(
                [
                    {
                        id: 0,
                        successors: [],
                        predecessors: [],
                        stmts: [{ _: "AssignStmt", left: local("x"), right: rawValue }, { _: "ReturnVoidStmt" }],
                    },
                ],
                ["x"],
            ),
        );
        expect(ok).toEqual([]);

        // Kotlin strips CastExpr on the LHS, so CastExpr(Local) := <raw> is a
        // Local assignment too and must not be flagged.
        const okCast = violations(
            bodyWithBlocks(
                [
                    {
                        id: 0,
                        successors: [],
                        predecessors: [],
                        stmts: [
                            {
                                _: "AssignStmt",
                                left: { _: "CastExpr", arg: local("x"), type: UNKNOWN_TYPE },
                                right: rawValue,
                            },
                            { _: "ReturnVoidStmt" },
                        ],
                    },
                ],
                ["x"],
            ),
        );
        expect(okCast).toEqual([]);
    });

    it("requires 'type' on raw fallback values", () => {
        const rawValue = { _: "SomeExoticValue", whatever: 1 } as unknown as ValueDto;
        const errs = violations(
            bodyWithBlocks([
                {
                    id: 0,
                    successors: [],
                    predecessors: [],
                    stmts: [{ _: "AssignStmt", left: local("x"), right: rawValue }, { _: "ReturnVoidStmt" }],
                },
            ], ["x"]),
        );
        expect(errs.some((e) => e.includes("missing required 'type'"))).toBe(true);
    });

    it("rejects bad assign targets", () => {
        const errs = violations(
            bodyWithBlocks(
                [
                    {
                        id: 0,
                        successors: [],
                        predecessors: [],
                        stmts: [
                            {
                                _: "AssignStmt",
                                left: { _: "BinopExpr", op: "+", left: local("a"), right: local("b") },
                                right: local("a"),
                            },
                            { _: "ReturnVoidStmt" },
                        ],
                    },
                ],
                ["a", "b"],
            ),
        );
        expect(errs.some((e) => e.includes("AssignStmt.left has kind 'BinopExpr'"))).toBe(true);
    });

    it("checks predecessor/successor consistency", () => {
        const errs = violations(
            bodyWithBlocks([
                { id: 0, successors: [1], predecessors: [], stmts: [{ _: "NopStmt" }] },
                { id: 1, successors: [], predecessors: [], stmts: [{ _: "ReturnVoidStmt" }] },
            ]),
        );
        expect(errs.some((e) => e.includes("predecessors [] inconsistent"))).toBe(true);
    });
});
