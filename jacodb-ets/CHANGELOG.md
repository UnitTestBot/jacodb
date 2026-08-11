# jacodb-ets changelog

## Unreleased

### Added

- Native TypeScript frontend (`jacodb-ets/ts-frontend`) bundled into the `jacodb-ets` JAR
  and used by default for TS/JS input. ArkTS (`.ets`) and SDK trees keep using the legacy
  ArkAnalyzer provider. The provider can be forced with the `ETS_IR_PROVIDER` environment
  variable (`ts-frontend` / `arkanalyzer`). Building or publishing `jacodb-ets` requires
  Node.js and npm; every successful artifact contains a runtime built from the checkout.
- `EtsStmtLocation.origin` (`EtsSourceSpan`): the source range a statement was lowered from.
  Only the TS frontend emits origins; with ArkAnalyzer it is always `null`.
  It is a mutable property outside the primary constructor and therefore does NOT participate
  in `equals` / `hashCode` of `EtsStmtLocation` or of any `EtsStmt`. For the same reason the
  generated `EtsStmtLocation.copy()` does not carry `origin` (a copy always has `origin == null`);
  use the three-argument constructor or `EtsStmtLocation.stub(...)` to preserve it.

### Changed (breaking)

- `EtsClosureFieldRef` now implements `EtsLValue`, so it can legally appear as
  `EtsAssignStmt.lhv`. `EtsLValue` is not sealed, so nothing inside the repository breaks,
  but **external analyses with an exhaustive `when` over LHV kinds and an `else -> error()`
  branch must handle `EtsClosureFieldRef`.**
- `generateEtsIR` no longer logs a failure and returns the output path: it now throws
  `EtsIrGenerationException` on a non-zero exit code or a timeout. Partial output is kept
  on disk (its path is included in the exception message) and the full stdout/stderr is
  written to the log; the exception message itself is truncated.
- The default `generateEtsIR` timeout is no longer 10 seconds: it is 60 seconds for a single
  file and 10 minutes in project mode, and it can be overridden with the
  `ETS_IR_GENERATION_TIMEOUT_SEC` environment variable.
- `loadEtsProjectAutoConvert` now resolves its default provider with `defaultProviderFor(...)`
  (consistent with `generateEtsIR` / `loadEtsFileAutoConvert`), so an ArkTS project is no
  longer silently converted into an empty `EtsScene`.
- `generateSdkIR` explicitly forces the ArkAnalyzer provider: SDK trees consist of declaration
  files, which the native frontend deliberately skips.
- Binary compatibility: `EtsMethodBuilder`, `EtsStmtLocation` and the `loadEts*AutoConvert`
  helpers changed their signatures; recompilation of downstream consumers is required.
- `SourceSpanDto.fileName` is now optional in the wire format: frontends omit it for spans
  belonging to the enclosing file, and the Kotlin side falls back to the file signature.
