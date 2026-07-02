# ts-frontend — native TypeScript frontend for jacodb-ets

Parses TypeScript/JavaScript with the real TypeScript compiler and lowers it
directly into **EtsIR**: three-address code organized into a basic-block CFG,
serialized as JSON that the Kotlin side deserializes with
`EtsFileDto.loadFromJson` (see `jacodb-ets/src/main/kotlin/org/jacodb/ets/dto/`).

It replaces the external [ArkAnalyzer](../ARKANALYZER.md) dependency while
remaining wire-compatible with its output format. Both frontends stay
supported — see [Provider selection](#provider-selection).

## Build & test

```shell
cd jacodb-ets/ts-frontend
npm ci
npm run build   # -> dist/
npm test        # vitest unit + corpus tests
```

Or via Gradle (used automatically by `:jacodb-ets:test`):

```shell
./gradlew :jacodb-ets:buildTsFrontend :jacodb-ets:testTsFrontend
```

## CLI

```
node dist/index.js [-p] [-e] [-t N] [--multi] [-v] <input> <output>
```

- default: `<input>` is a single `.ts`/`.js` file, `<output>` is the JSON file;
- `-p` / `--multi`: `<input>` is a directory; one shared compiler program is
  built over all sources and a `<relative-path>.json` is written per file,
  mirroring the input tree under `<output>`;
- `-e`, `-t N`: accepted for CLI compatibility with ArkAnalyzer's
  `serializeArkIR` (type inference is always on here);
- `-v`: verbose logging to stderr (diagnostics about degraded constructs).

The frontend never fails on exotic-but-parseable input: anything it cannot
lower degrades to `RawStmt`/`RawValue` fallbacks (`"_": "Unsupported*"`), and
every produced file is checked against the Kotlin-side structural invariants
(`src/validate.ts`) before being written.

## Architecture

```
src/
  dto/            TS mirrors of the Kotlin DTOs (wire format, discriminator "_")
  types/convert.ts  ts.TypeNode / checker.Type -> TypeDto
  lowering/
    fileBuilder.ts   file/namespace scopes, %dflt classes, imports/exports
    classBuilder.ts  classes (ctor + %instInit/%statInit), interfaces, enums
    methodBuilder.ts locals table, %N temps, prologue (params + this := ThisRef)
    cfg.ts           label/terminator CFG builder; DTO successor conventions
    exprLowering.ts  expressions -> three-address values (immediate operands)
    stmtLowering.ts  control flow, try/catch, destructuring
  serialize.ts    DTO -> JSON
  validate.ts     invariant checker (runs before every write)
```

Key conventions (shared with ArkAnalyzer, enforced by tests):

- every file gets a `%dflt` class; loose top-level statements form its `%dflt`
  method; free functions become its methods;
- method prologue: `param := ParameterRef(i)`, then `this := ThisRef`;
- classes get `%instInit`/`%statInit` initializers and a constructor that calls
  `%instInit` and returns `this`;
- for a block ending with `IfStmt`, DTO successors are `[false, true]`
  (the Kotlin `Convert` reverses successor lists);
- closures become `%AM<n>$<method>` methods, object literals become `%AC<n>`
  anonymous classes;
- `InstanceCallExpr.instance` / `InstanceFieldRef.instance` are always Locals,
  operands and call arguments are immediates (`Local`/`Constant`), temps are
  named `%0, %1, …` (never `_tmp*` — reserved by the Kotlin converter).

## Provider selection

The Kotlin side (`org.jacodb.ets.utils.LoadEtsFile`) picks the IR generator via
`EtsIrProvider`:

| Provider | Requirements | Selection |
|---|---|---|
| `TS_FRONTEND` (default) | `npm run build` in this directory; `node` on PATH | `ETS_IR_PROVIDER=ts-frontend` |
| `ARKANALYZER` | `ARKANALYZER_DIR` pointing to a built ArkAnalyzer checkout | `ETS_IR_PROVIDER=arkanalyzer` |

Environment variables understood by the TS provider:

- `ETS_FRONTEND_DIR` (or the `ets.frontend.dir` system property, set by Gradle)
  — location of this directory; defaults to `ts-frontend` relative to CWD;
- `ETS_FRONTEND_SCRIPT` — script path inside it, default `dist/index.js`;
- `NODE_EXECUTABLE` — node binary, default `node`.
