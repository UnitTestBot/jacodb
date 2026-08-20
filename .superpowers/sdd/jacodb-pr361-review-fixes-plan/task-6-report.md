# Task 6 Report: Extract stable decorator names

## Implementation

- Added recursive `decoratorName` extraction in `jacodb-ets/ts-frontend/src/lowering/astUtils.ts`.
- Call expressions are unwrapped, property-access chains are joined, identifiers use their text, and unsupported expressions retain the complete `getText()` fallback.
- Removed the decorator-path `slice(0, 50)` truncation.
- Added a class-lowering regression test covering `@sealed`, `@factory(1)`, `@ns.decorator`, and `@very.long.namespace.decorator(1)` with literal expected names.

## TDD evidence

### RED

Command:

```text
npm test -- test/classes.spec.ts
```

Result: 1 failed, 20 passed. The new test received `very.long.namespace.decorator(1)` instead of the expected `very.long.namespace.decorator`; this confirmed the old fallback did not unwrap called property-access decorators.

### GREEN

Commands:

```text
npm run typecheck && npm test -- test/classes.spec.ts
npm test
```

Results: typecheck passed; focused suite passed with 21/21 tests; full suite passed with 201/201 tests across 13 files.

## Files changed

- `jacodb-ets/ts-frontend/src/lowering/astUtils.ts`
- `jacodb-ets/ts-frontend/test/classes.spec.ts`

## Self-review

- `git diff --check` passed.
- Confirmed no `slice(0, 50)` remains in the decorator lowering path.
- No unrelated files or lowering/contracts were changed.

## Concerns

None.
