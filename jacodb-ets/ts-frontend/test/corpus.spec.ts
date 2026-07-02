import * as fs from "fs";
import * as path from "path";
import { describe, expect, it } from "vitest";
import { EtsFileDto } from "../src/dto/model";
import { lower } from "./util";

const FIXTURES_DIR = path.join(__dirname, "fixtures");

/** Count raw fallback statements/values (any "Unsupported*" discriminator). */
function countRawFallbacks(file: EtsFileDto): number {
    let count = 0;
    const visit = (value: unknown): void => {
        if (Array.isArray(value)) {
            value.forEach(visit);
            return;
        }
        if (value !== null && typeof value === "object") {
            const kind = (value as { _?: string })._;
            if (kind !== undefined && kind.startsWith("Unsupported")) {
                count++;
            }
            Object.values(value).forEach(visit);
        }
    };
    visit(file);
    return count;
}

describe("corpus: realistic TS/JS programs lower cleanly", () => {
    const fixtures = fs.readdirSync(FIXTURES_DIR).filter((f) => f.endsWith(".ts") || f.endsWith(".js"));

    it("has fixtures to run", () => {
        expect(fixtures.length).toBeGreaterThanOrEqual(5);
    });

    for (const fixture of fixtures) {
        it(`lowers ${fixture} with zero invariant violations and zero raw fallbacks`, () => {
            const source = fs.readFileSync(path.join(FIXTURES_DIR, fixture), "utf-8");
            // lower() throws on any invariant violation.
            const { file, diagnostics } = lower(source, "corpus", fixture);
            expect(countRawFallbacks(file)).toBe(0);
            expect(diagnostics.messages).toEqual([]);

            // Sanity: every method body has at least the prologue and a terminator.
            const allMethods = [
                ...file.classes.flatMap((c) => c.methods),
                ...file.namespaces.flatMap(function collect(ns): typeof file.classes[0]["methods"] {
                    return [
                        ...(ns.classes ?? []).flatMap((c) => c.methods),
                        ...(ns.namespaces ?? []).flatMap(collect),
                    ];
                }),
            ];
            for (const method of allMethods) {
                if (method.body !== undefined) {
                    const stmts = method.body.cfg.blocks.flatMap((b) => b.stmts);
                    expect(stmts.length).toBeGreaterThanOrEqual(2);
                }
            }
        });
    }
});
