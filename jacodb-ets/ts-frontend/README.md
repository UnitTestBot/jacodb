# ts-frontend — native TypeScript frontend for jacodb-ets

`ts-frontend` parses TypeScript/JavaScript with the real TypeScript compiler
and lowers it directly into **EtsIR** — three-address code organized into a
basic-block CFG — serialized as JSON that the Kotlin side of `jacodb-ets`
deserializes with `EtsFileDto.loadFromJson` and converts into `EtsFile` /
`EtsScene` model objects.

It is a drop-in replacement for the external [ArkAnalyzer](../ARKANALYZER.md)
dependency: wire-compatible output, same CLI shape, no external checkout
required. Both frontends remain supported and selectable
(see [Choosing a provider](#choosing-a-provider)).

```
   .ts / .js sources                      JVM (jacodb-ets)
        │                                       ▲
        ▼                                       │ EtsFileDto.loadFromJson
  ts.createProgram ──► lowering ──► EtsIR JSON ─┘      + Convert.kt
  (parse + types)   (3-addr code,                ──► EtsFile / EtsScene
                     block CFG)
```

## Prerequisites

- **Node.js ≥ 18** and **npm** on `PATH` (that is the only requirement —
  the frontend has a single runtime dependency, the `typescript` package).

## Quick start

### 1. Build

```shell
cd jacodb-ets/ts-frontend
npm ci
npm run build      # compiles src/ -> dist/
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

## Using from Kotlin

The main entry points live in `org.jacodb.ets.utils` (`LoadEtsFile.kt`).
With the frontend built, no configuration is needed when running from the
`jacodb-ets` module directory (Gradle sets `ets.frontend.dir` for tests
automatically):

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

### Locating the frontend

When your working directory is not `jacodb-ets`, tell the loader where the
frontend lives (any one of these):

| Mechanism | Example |
|---|---|
| env var | `ETS_FRONTEND_DIR=/path/to/jacodb/jacodb-ets/ts-frontend` |
| system property | `-Dets.frontend.dir=/path/to/jacodb/jacodb-ets/ts-frontend` |
| default | `ts-frontend` relative to the current working directory |

Additional knobs: `ETS_FRONTEND_SCRIPT` (default `dist/index.js`),
`NODE_EXECUTABLE` (default `node`).

### Choosing a provider

`EtsIrProvider` selects which frontend generates the IR. The default is
`TS_FRONTEND`; the legacy ArkAnalyzer path is fully preserved:

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

  -p, --project    treat <input> as a project directory
  -m, --multi      same as -p (kept for serializeArkIR compatibility)
  -e               accepted for compatibility (entrypoints), no-op
  -t N             accepted for compatibility (type inference level), no-op —
                   checker-based type inference is always on
  -v, --verbose    log progress and degradation diagnostics to stderr
```

Exit codes: `0` — success, `1` — I/O or invariant failure, `2` — bad usage.
Stdout is never used for data; diagnostics go to stderr.

**Robustness guarantees.** The frontend never fails on exotic-but-parseable
input: any construct it cannot model degrades to a `RawStmt`/`RawValue`
fallback (`"_": "Unsupported*"`, deserialized by Kotlin into
`RawStmtDto`/`RawValueDto`) plus a `-v` diagnostic. Every produced file is
checked against the Kotlin-side structural invariants (`src/validate.ts`)
before being written — invalid IR is never emitted.

## Supported language features

Fully lowered to IR: literals and template strings; all `Ops.kt` operators;
variables and compound assignments; free/method/static/pointer calls; `new` +
constructor calls; arrays and element access; fields (instance and static,
including `this.f` in static methods); `if`/ternary; `while`/`do`/`for`/
`for-of` (iterator protocol)/`for-in`; `switch` with fallthrough;
`break`/`continue` (incl. labeled); `return`/`throw`; `try/catch/finally`
(catch handlers stay reachable and bind `CaughtExceptionRef`); classes with
`%instInit`/`%statInit`/constructors, inheritance, parameter properties,
static blocks; interfaces (bodyless methods); enums (numeric auto-increment
and string); namespaces (nested); closures (`%AM` methods); object literals
(`%AC` classes); object/array destructuring with defaults and nesting;
optional chaining; `super()`/`super.m()`; `typeof`/`await`/`yield`/`delete`/
`void`/`instanceof`/`as`-casts; imports/exports of every flavor.

Degrades to `Raw*` fallbacks (for now): regex literals, tagged templates,
spread arguments/elements, rest patterns in destructuring, accessors/spread
in object literals, variable capture via `LexicalEnvType` (captured outer
variables become same-named locals instead).

## IR conventions

The output follows the ArkAnalyzer conventions expected by `Convert.kt`
(see `../src/main/kotlin/org/jacodb/ets/dto/`):

- every file gets a `%dflt` class; loose top-level statements form its `%dflt`
  method; free functions become its methods;
- method prologue: `param_i := ParameterRef(i)` per parameter, then
  `this := ThisRef`;
- classes get synthesized `%instInit`/`%statInit` initializers; constructors
  call `%instInit` and `return this` (a default constructor is synthesized
  when absent);
- basic blocks are listed with `id == index`, entry block is `0`; a block
  ending with `IfStmt` has successors `[falseBranch, trueBranch]` (the Kotlin
  converter reverses every successor list), any other block has ≤ 1 successor;
- call instances and field-ref instances are always `Local`s; expression
  operands, call arguments and array indices are immediates (`Local` /
  `Constant`), hoisted into `%0, %1, …` temps (never `_tmp*` — that prefix is
  reserved by the Kotlin converter);
- truthiness in conditions is normalized as `v != false` (booleans) /
  `v != 0` (everything else).

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
| `Script file not found: '.../dist/index.js'` | run `npm run build` (or `./gradlew :jacodb-ets:buildTsFrontend`) |
| `ts-frontend directory does not exist` | set `ETS_FRONTEND_DIR` / `-Dets.frontend.dir` (see above) |
| Kotlin tests are skipped with "ts-frontend is not built" | same as above — the tests skip instead of failing when the frontend is missing |
| `npm is not available; skipping ts-frontend build` in Gradle | install Node.js ≥ 18 and ensure `npm` is on `PATH` |
| Constructs missing from the IR | run the CLI with `-v` — every degraded construct is reported to stderr |
