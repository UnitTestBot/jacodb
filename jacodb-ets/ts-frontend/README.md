# ts-frontend

`ts-frontend` parses TypeScript and JavaScript with the TypeScript compiler and
emits EtsIR JSON for `jacodb-ets`. The Kotlin side loads that JSON through
`EtsFileDto` and converts it to `EtsFile` / `EtsScene`.

Its output is compatible with the EtsIR wire format consumed by `jacodb-ets`.
That wire format is the compatibility boundary: ArkAnalyzer is an alternative
provider for the cases below, not a semantic oracle for the native frontend.

## Prerequisites

- Node.js 18.18 or newer on `PATH`.
- `npm` to build this checkout. The packaged `jacodb-ets` runtime includes the
  frontend and the matching TypeScript `lib*.d.ts` files; conversion does not
  install dependencies.

## CLI

Build the frontend, then convert a file:

```shell
cd jacodb-ets/ts-frontend
npm ci
npm run build
node dist/index.js path/to/app.ts out/app.ts.json
```

For a directory, `--project` applies its `tsconfig.json`; `--multi` recursively
converts supported TS/JS files without applying `tsconfig.json`:

```shell
node dist/index.js --project path/to/project out/ir/
node dist/index.js --multi path/to/sources out/ir/
```

Run `node dist/index.js` without arguments to see the complete option syntax.
Use `-v` for lowering diagnostics on stderr.

## Kotlin use and provider selection

TS/JS uses `EtsIrProvider.TS_FRONTEND` by default. Automatic selection switches
to `EtsIrProvider.ARKANALYZER` for a single `.ets` file, or for a project
containing a visible `.ets` file outside hidden directories and `node_modules`.
Select either provider explicitly when required. ArkAnalyzer requires
`ARKANALYZER_DIR`.

```kotlin
import kotlin.io.path.Path
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert

val file = loadEtsFileAutoConvert(
    Path("src/app.ts"),
    provider = EtsIrProvider.TS_FRONTEND,
)
```

```shell
cd ../..
ETS_IR_PROVIDER=ts-frontend ./gradlew :jacodb-ets:generateTestResources
ETS_IR_PROVIDER=arkanalyzer ARKANALYZER_DIR=/path/to/arkanalyzer \
  ./gradlew :jacodb-ets:generateTestResources
```

`generateSdkIR()` deliberately forces ArkAnalyzer because SDK declaration files
are not input to the native frontend. For native-frontend development, override
the bundled runtime with `ETS_FRONTEND_DIR=/path/to/jacodb-ets/ts-frontend` (or
the `ets.frontend.dir` system property); `ETS_FRONTEND_SCRIPT` and
`NODE_EXECUTABLE` provide further runtime overrides.

## Semantics and limitations

`IfStmt.condition` stores the lowered source value or expression. It is not
rewritten to a comparison with `true`; interpreters must apply TypeScript
truthiness when choosing the successor.

The native frontend owns TS/JS, not ArkTS. Unsupported source constructs may be
represented as `RawStmt` or `RawValue` and reported with `-v`. Exception CFGs
also approximate `try`/`catch` reachability because EtsIR has no trap table or
exceptional-successor edges. Consumers that need exact behavior should account
for these approximations.

## Development

```shell
cd jacodb-ets/ts-frontend
npm run typecheck
npm test
npm run build

# Kotlin integration path
cd ../..
./gradlew :jacodb-ets:testTsFrontend
```
