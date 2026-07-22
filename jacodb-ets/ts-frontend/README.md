# ts-frontend — native TypeScript frontend for jacodb-ets

`ts-frontend` parses TypeScript/JavaScript with the real TypeScript compiler
and lowers it directly into **EtsIR** — three-address code organized into a
basic-block CFG — serialized as JSON that the Kotlin side of `jacodb-ets`
deserializes with `EtsFileDto.loadFromJson` and converts into `EtsFile` /
`EtsScene` model objects.

For TypeScript and JavaScript it replaces the external
[ArkAnalyzer](../ARKANALYZER.md) dependency with wire-compatible output and
the same CLI shape. ArkTS `.ets` sources deliberately remain on the legacy
ArkAnalyzer provider (see [Choosing a provider](#choosing-a-provider)).

```
   .ts / .js sources                      JVM (jacodb-ets)
        │                                       ▲
        ▼                                       │ EtsFileDto.loadFromJson
  ts.createProgram ──► lowering ──► EtsIR JSON ─┘      + Convert.kt
  (parse + types)   (3-addr code,                ──► EtsFile / EtsScene
                     block CFG)
```

## Prerequisites

- **Node.js ≥ 18** on `PATH` at runtime.
- **npm** is needed only when building this repository. The Gradle build bundles
  the compiler plus the matching TypeScript `lib*.d.ts` standard libraries into
  the `jacodb-ets` JAR. Runtime conversion does not download dependencies.

## Quick start

### 1. Build

```shell
cd jacodb-ets/ts-frontend
npm ci
npm run build      # type-checks and creates dist/index.js + matching lib*.d.ts
```

Or let Gradle do it (also happens automatically before `:jacodb-ets:test`):

```shell
./gradlew :jacodb-ets:buildTsFrontend
```

### 2. Run on a single file

```shell
node dist/index.js path/to/app.ts out/app.ts.json
```

`out/app.ts.json` now contains a complete `EtsFileDto`: the `%dflt` class with
lowered top-level code, all declared classes/interfaces/enums/namespaces with
method bodies as basic-block CFGs, import/export infos.

### 3. Run on a directory (project mode)

```shell
node dist/index.js -p path/to/project out/ir/
# or, equivalently for a flat directory of samples:
node dist/index.js --multi path/to/sources out/ir/
```

One shared compiler program is built over all sources (so cross-file
references resolve to proper signatures), and a `<relative-path>.json` is
written per source file, mirroring the input tree under the output directory.
`--project` finds and applies `tsconfig.json` (including `extends`, compiler
options, and include/exclude rules); `--multi` deliberately ignores it and
recursively converts all supported source files. Both modes include
`.ts/.tsx/.js/.jsx/.mts/.cts/.mjs/.cjs` sources. Use ArkAnalyzer for `.ets`.

## Using from Kotlin

The main entry points live in `org.jacodb.ets.utils` (`LoadEtsFile.kt`).
No frontend checkout, network access, or working-directory setup is required:
the script and its standard-library declarations are extracted together from
the `jacodb-ets` JAR automatically.

```kotlin
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.jacodb.ets.model.EtsScene

// Single file -> EtsFile
val etsFile = loadEtsFileAutoConvert(Path("src/app.ts"))
val scene = EtsScene(listOf(etsFile))

// Whole project directory -> EtsScene
val projectScene = loadEtsProjectAutoConvert(Path("path/to/project"))

// Work with the IR
for (cls in scene.projectClasses) {
    for (method in cls.methods) {
        println("${cls.name}::${method.name}")
        method.cfg.stmts.forEach(::println)   // linearized three-address code
    }
}
```

Lower-level pieces, if you need the raw DTO or the JSON file:

```kotlin
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR

val jsonPath = generateEtsIR(Path("src/app.ts"), provider = EtsIrProvider.TS_FRONTEND)
val dto = EtsFileDto.loadFromJson(jsonPath.readText())
val file = dto.toEtsFile()
```

### Overriding the bundled frontend

The bundled runtime is the default. For frontend development, it can be
overridden with a local checkout:

| Mechanism | Example |
|---|---|
| env var | `ETS_FRONTEND_DIR=/path/to/jacodb/jacodb-ets/ts-frontend` |
| system property | `-Dets.frontend.dir=/path/to/jacodb/jacodb-ets/ts-frontend` |
| default | bundled `ets-frontend/runtime.zip` from the JAR |

Additional knobs: `ETS_FRONTEND_SCRIPT` (default `dist/index.js`),
`NODE_EXECUTABLE` (default `node`).

### Choosing a provider

`EtsIrProvider` selects which frontend generates the IR. The default is
`TS_FRONTEND` for TS/JS; a single `.ets` file or a project containing `.ets`
defaults to `ARKANALYZER`. The legacy path is fully preserved and can always
be selected explicitly:

```kotlin
// explicitly per call:
loadEtsFileAutoConvert(path, provider = EtsIrProvider.ARKANALYZER)
loadEtsProjectAutoConvert(path, provider = EtsIrProvider.TS_FRONTEND)
```

```shell
# or globally via the environment:
export ETS_IR_PROVIDER=arkanalyzer   # requires ARKANALYZER_DIR (see ../ARKANALYZER.md)
export ETS_IR_PROVIDER=ts-frontend   # the default
```

The same switch drives the Gradle task that (re)generates test resources:

```shell
./gradlew :jacodb-ets:generateTestResources                          # ts-frontend
ETS_IR_PROVIDER=arkanalyzer ./gradlew :jacodb-ets:generateTestResources
```

## CLI reference

```
node dist/index.js [options] <input> <output>

  <input>          source file, or a directory with -p/--multi
  <output>         output JSON file, or a directory with -p/--multi

  -p, --project    apply tsconfig.json and convert its project roots
  -m, --multi      recursively convert sources, ignoring tsconfig.json
  -e               accepted for compatibility (entrypoints), no-op
  -t N             accepted for compatibility (type inference level), no-op —
                   checker-based type inference is always on
  -v, --verbose    log progress and degradation diagnostics to stderr
```

Exit codes: `0` — success, `1` — configuration, I/O, or invariant failure,
`2` — bad usage.
Stdout is never used for data; diagnostics go to stderr.

**Robustness guarantees.** The frontend never fails on exotic-but-parseable
input: any construct it cannot model degrades to a `RawStmt`/`RawValue`
fallback (`"_": "Unsupported*"`, deserialized by Kotlin into
`RawStmtDto`/`RawValueDto`) plus a `-v` diagnostic. Every produced file is
checked against the Kotlin-side structural invariants (`src/validate.ts`)
before being written — invalid IR is never emitted.

## Supported language features

Fully lowered to IR: literals and template strings; all `Ops.kt` operators;
variables, compound assignments, and short-circuiting logical operators;
module variables as shared `%dflt` static storage visible to free functions;
free/method/static/pointer calls; `new` + constructor calls; arrays and element
access; fields (instance and static,
including `this.f` in static methods); `if`/ternary; `while`/`do`/`for`/
`for-of` (iterator protocol)/`for-in`; `switch` with fallthrough;
`break`/`continue` (incl. labeled); `return`/`throw`; `try/catch/finally`
(catch handlers stay reachable and bind `CaughtExceptionRef`); classes with
`%instInit`/`%statInit`/constructors, inheritance, parameter properties, and
derived initialization after `super`; static blocks; interfaces (bodyless
methods); enums (numeric auto-increment and string); namespaces (nested);
closures (`%AM` methods) with mutable `LexicalEnvType`/`ClosureFieldRef`
captures, nested function-declaration lifting, and lexical `this`; object literals
(`%AC` classes); object/array destructuring with defaults and nesting;
optional chaining; `super()`/`super.m()`; `typeof`/`await`/`yield`/`delete`/
`void`/`instanceof`/`as`-casts; imports/exports of every flavor.

Project parsing supports TSX/JSX files; JSX element expressions currently
degrade to `RawValue`. Other `Raw*` fallbacks include regex literals, tagged
templates, spread arguments/elements, rest patterns in destructuring, and
accessors/spread in object literals.

## IR conventions

The output follows the ArkAnalyzer conventions expected by `Convert.kt`
(see `../src/main/kotlin/org/jacodb/ets/dto/`):

- every file gets a `%dflt` class; loose top-level statements form its `%dflt`
  method; free functions become its methods; module variables are static fields
  of this class so every method observes the same storage;
- method prologue: `param_i := ParameterRef(i)` per parameter, then
  `this := ThisRef`;
- classes get synthesized `%instInit`/`%statInit` initializers; base
  constructors call `%instInit` before their body, while derived constructors
  call `super` first, initialize parameter properties, and then call `%instInit`;
  `%statInit` preserves source order across static fields and blocks; every
  constructor returns `this` (a forwarding default constructor is synthesized
  when absent);
- basic blocks are listed with `id == index`, entry block is `0`; a block
  ending with `IfStmt` has successors `[falseBranch, trueBranch]` (the Kotlin
  converter reverses every successor list), any other block has ≤ 1 successor;
- call instances and field-ref instances are always `Local`s; expression
  operands, call arguments and array indices are immediates (`Local` /
  `Constant`), hoisted into `%0, %1, …` temps (never `_tmp*` — that prefix is
  reserved by the Kotlin converter);
- truthiness follows JavaScript `ToBoolean`: `false`, `0`, `NaN`, `""`, `null`,
  and `undefined` are false; objects/functions/arrays are true; unknown and
  union values receive the complete runtime check chain.

### Exception CFG approximation

The current EtsIR DTO has no trap table or exceptional-successor edge. To keep
both `try` and `catch` analyzable, the frontend emits a synthetic
nondeterministic entry branch: one successor enters the try body and the other
binds `CaughtExceptionRef` and enters the catch body. This intentionally
over-approximates reachability; it does not claim that an exception occurs at a
specific throwing statement. `finally` is shared on normal joins and duplicated
before abrupt exits (`return`, `throw`, and escaping `break`/`continue`). This is
the frontend's explicit exception-model contract until EtsIR grows real
exception edges.

### Source origins

For statements emitted from source code, a method body may contain a
`stmtOrigins` side table. Each entry identifies a statement by its final
`blockId` and `stmtIndex` and stores the originating file, syntax kind, and
source range. Offsets use UTF-16 code units (like the TypeScript compiler API),
and line/column pairs are zero-based. On the Kotlin side the range is available
as `stmt.location.origin`.

One source expression can lower to several three-address EtsIR statements, so
multiple entries may carry the same range. Synthetic prologue, control-flow,
and fallback statements may have no origin. Consumers must therefore treat the
mapping as many-to-one and optional rather than as a source-line identifier.

## Development

```shell
npm run typecheck   # tsc --noEmit
npm test            # vitest: unit specs + corpus
./gradlew :jacodb-ets:testTsFrontend   # the same via Gradle
```

Layout:

```
src/
  dto/              TS mirrors of the Kotlin DTOs (wire format, discriminator "_")
  types/convert.ts  ts.TypeNode / checker.Type -> TypeDto
  lowering/
    fileBuilder.ts    file/namespace scopes, %dflt classes, imports/exports
    classBuilder.ts   classes (ctor + %instInit/%statInit), interfaces, enums
    methodBuilder.ts  locals table, %N temps, prologue emission
    cfg.ts            label/terminator CFG builder; DTO successor conventions
    exprLowering.ts   expressions -> three-address values
    stmtLowering.ts   control flow, try/catch, destructuring
    diagnostics.ts    Raw* fallbacks + warnings
  serialize.ts      DTO -> JSON (undefined-stripping)
  validate.ts       invariant checker (runs before every write)
test/
  *.spec.ts         per-feature specs (types, lowering, cfg, classes, try,
                    imports, closures, serialize, validate, cli)
  corpus.spec.ts    realistic TS/JS fixtures must lower with zero invariant
                    violations and zero raw fallbacks
  fixtures/         the corpus itself (plain TS/JS programs)
```

When adding support for a new construct:

1. check the expected JSON shape against the ArkAnalyzer ground truth in
   `../src/test/resources/repos/<project>/etsir/` (schema compatibility is
   the contract, not byte identity);
2. add the lowering + a per-feature spec, extend `validate.ts` if the
   construct introduces a new invariant;
3. run the Kotlin round-trip: `./gradlew :jacodb-ets:test` (the
   `EtsTsFrontendTest` suite drives the production `generateEtsIR` path).

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Script file not found: '.../dist/index.js'` | an explicit frontend override is active; build that checkout or remove the override |
| `ts-frontend directory does not exist` | an explicit `ETS_FRONTEND_DIR` / `-Dets.frontend.dir` points to a missing checkout |
| `npm is not available; skipping ts-frontend build` in Gradle | install Node.js ≥ 18 and ensure `npm` is on `PATH` |
| Constructs missing from the IR | run the CLI with `-v` — every degraded construct is reported to stderr |
