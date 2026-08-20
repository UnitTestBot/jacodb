/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * Basic-block CFG builder.
 *
 * Lowering code works with symbolic labels and explicit terminators
 * (goto / if / return / throw); `finalize()` then renumbers reachable blocks
 * (entry = 0, id == index), materializes IfStmt terminators, and computes
 * successor lists in the DTO convention:
 *   - a block ending with IfStmt has successors [falseBranch, trueBranch]
 *     (the Kotlin Convert reverses every successor list);
 *   - any other block has at most one successor.
 */

import { BasicBlockDto, CfgDto, SourceSpanDto, StmtOriginDto } from "../dto/model";
import { RETURN_VOID_STMT, StmtDto } from "../dto/stmts";
import { ValueDto } from "../dto/values";

export type Label = number;

type Terminator =
    | { kind: "goto"; target: Label }
    | { kind: "if"; condition: ValueDto; trueTarget: Label; falseTarget: Label; origin?: SourceSpanDto }
    | { kind: "return"; arg?: ValueDto; origin?: SourceSpanDto }
    | { kind: "throw"; arg: ValueDto; origin?: SourceSpanDto }
    | { kind: "open" }; // fall-through end; finalize() turns it into `return void`

interface LocatedStmt {
    stmt: StmtDto;
    origin?: SourceSpanDto;
}

interface BuilderBlock {
    label: Label;
    stmts: LocatedStmt[];
    terminator: Terminator;
}

export interface FinalizedCfg {
    cfg: CfgDto;
    stmtOrigins: StmtOriginDto[];
}

export class CfgBuilder {
    private readonly blocks: (BuilderBlock | undefined)[] = [];
    private current: BuilderBlock;
    private nextLabel: Label = 0;
    private readonly entry: Label;
    private currentOrigin: SourceSpanDto | undefined;

    constructor() {
        this.entry = this.newLabel();
        this.current = this.place(this.entry);
    }

    /** Allocate a fresh label; the block must later be placed with `placeLabel`. */
    newLabel(): Label {
        const label = this.nextLabel++;
        this.blocks.push(undefined);
        return label;
    }

    private place(label: Label): BuilderBlock {
        if (this.blocks[label] !== undefined) {
            throw new Error(`label ${label} is already placed`);
        }
        const block: BuilderBlock = { label, stmts: [], terminator: { kind: "open" } };
        this.blocks[label] = block;
        return block;
    }

    /**
     * Start emitting into the block for `label`.
     * If the current block is still open, it falls through (goto) to `label`.
     */
    placeLabel(label: Label): void {
        if (this.current.terminator.kind === "open") {
            this.current.terminator = { kind: "goto", target: label };
        }
        this.current = this.place(label);
    }

    /** Whether the current block is unterminated (still accepts statements). */
    isOpen(): boolean {
        return this.current.terminator.kind === "open";
    }

    /** Attribute all statements/terminators emitted by [action] to [origin]. */
    withOrigin<T>(origin: SourceSpanDto | undefined, action: () => T): T {
        const previous = this.currentOrigin;
        this.currentOrigin = origin;
        try {
            return action();
        } finally {
            this.currentOrigin = previous;
        }
    }

    /**
     * Unreachable code after return/throw/etc: continue in a detached block so
     * lowering can proceed; it is dropped by reachability in `finalize()`.
     */
    private ensureOpen(): void {
        if (!this.isOpen()) {
            this.current = this.place(this.newLabel());
        }
    }

    emit(stmt: StmtDto): void {
        this.ensureOpen();
        this.current.stmts.push({ stmt, origin: this.currentOrigin });
    }

    goto(target: Label): void {
        this.ensureOpen();
        this.current.terminator = { kind: "goto", target };
    }

    branch(condition: ValueDto, trueTarget: Label, falseTarget: Label): void {
        this.ensureOpen();
        this.current.terminator = { kind: "if", condition, trueTarget, falseTarget, origin: this.currentOrigin };
    }

    ret(arg?: ValueDto): void {
        this.ensureOpen();
        this.current.terminator = { kind: "return", arg, origin: this.currentOrigin };
    }

    throwValue(arg: ValueDto): void {
        this.ensureOpen();
        this.current.terminator = { kind: "throw", arg, origin: this.currentOrigin };
    }

    /**
     * Produce the final CfgDto: reachable blocks only, renumbered from the entry
     * in DFS order (true branch first), successors in the DTO convention,
     * predecessors computed.
     */
    finalize(): FinalizedCfg {
        for (const block of this.blocks) {
            if (block === undefined) {
                throw new Error("finalize() with unplaced labels");
            }
        }
        const placed = this.blocks as BuilderBlock[];

        // Reachability + stable renumbering (DFS from entry, successor order).
        const order: BuilderBlock[] = [];
        const idOf = new Map<Label, number>();
        const visit = (label: Label): void => {
            if (idOf.has(label)) return;
            const block = placed[label];
            idOf.set(label, order.length);
            order.push(block);
            for (const succ of terminatorTargets(block.terminator)) {
                visit(succ);
            }
        };
        visit(this.entry);

        const stmtOrigins: StmtOriginDto[] = [];
        const result: BasicBlockDto[] = order.map((block, id) => {
            const locatedStmts = [...block.stmts];
            let successors: number[];
            const t = block.terminator;
            switch (t.kind) {
                case "goto":
                    successors = [idOf.get(t.target)!];
                    break;
                case "if":
                    locatedStmts.push({ stmt: { _: "IfStmt", condition: t.condition }, origin: t.origin });
                    // DTO convention: [false, true].
                    successors = [idOf.get(t.falseTarget)!, idOf.get(t.trueTarget)!];
                    break;
                case "return":
                    locatedStmts.push({
                        stmt: t.arg === undefined ? RETURN_VOID_STMT : { _: "ReturnStmt", arg: t.arg },
                        origin: t.origin,
                    });
                    successors = [];
                    break;
                case "throw":
                    locatedStmts.push({ stmt: { _: "ThrowStmt", arg: t.arg }, origin: t.origin });
                    successors = [];
                    break;
                case "open":
                    // Fall-through method end.
                    locatedStmts.push({ stmt: RETURN_VOID_STMT });
                    successors = [];
                    break;
            }
            const stmts = locatedStmts.map(({ stmt }) => stmt);
            locatedStmts.forEach(({ origin }, stmtIndex) => {
                if (origin !== undefined) {
                    stmtOrigins.push({ blockId: id, stmtIndex, source: origin });
                }
            });
            return { id, successors, predecessors: [], stmts };
        });

        // Predecessors.
        for (const block of result) {
            for (const succ of block.successors) {
                result[succ].predecessors!.push(block.id);
            }
        }

        return { cfg: { blocks: result }, stmtOrigins };
    }
}

function terminatorTargets(t: Terminator): Label[] {
    switch (t.kind) {
        case "goto":
            return [t.target];
        case "if":
            // Visit the TRUE branch first so it gets the smaller id
            // (matches natural source order for `if (c) {then} else {else}`).
            return [t.trueTarget, t.falseTarget];
        default:
            return [];
    }
}
