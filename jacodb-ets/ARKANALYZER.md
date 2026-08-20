# ArkAnalyzer

> **Note:** ArkAnalyzer is now the LEGACY EtsIR provider. The default provider
> is the native TypeScript frontend in [`ts-frontend`](ts-frontend/README.md),
> which requires no external checkout. ArkAnalyzer remains fully supported:
> select it with `ETS_IR_PROVIDER=arkanalyzer` (plus `ARKANALYZER_DIR`) or by
> passing `EtsIrProvider.ARKANALYZER` to the `loadEts*AutoConvert` functions.

## Installation

Clone and install the ArkAnalyzer via NPM:

```shell
cd ~/dev
git clone https://gitcode.com/openharmony-sig/arkanalyzer
cd arkanalyzer
npm install
npm run build
```

The `npm run build` command will generate the `out` directory, which contains the compiled code.

### Usage of forked ArkAnalyzer

Most probably, you will have to use our fork of ArkAnalyzer (https://gitcode.com/Lipen/arkanalyzer) and checkout a specific branch that is consistent with the current state of jacodb.
For this, replace the repo url in the commands above and use `git switch <branch>` to checkout the desired branch.

The ArkAnalyzer version exercised by CI is branch `neo/2025-09-03`, pinned to
commit `d9d7d5ffddb1f4081f90a73836c865794ec6a88c` for reproducible builds.

## Serialize TS to JSON

To serialize ArkIR to JSON for TypeScript files/projects, use the `serializeArkIR.ts` script:

```shell
$ npx ts-node ~/dev/arkanalyzer/src/save/serializeArkIR.ts --help
Usage: serializeArkIR [options] <input> <output>

Serialize ArkIR for TypeScript files or projects to JSON

Arguments:
  input          Input file or directory
  output         Output file or directory

Options:
  -m, --multi    Flag to indicate the input is a directory (default: false)
  -p, --project  Flag to indicate the input is a project directory (default: false)
  -v, --verbose  Verbose output (default: false)
  -h, --help     display help for command
```

You can also use `node <out>/serializeArkIR.js` directly (note the `.js` extension here!) instead of `npx ts-node`.
Remember to run `npm run build` beforehand.

For example, to serialize ArkIR for all TS files in `resources/ts/` into the corresponding JSON files in `resources/ir/`, run:

```shell
cd .../resources
npx ts-node ~/dev/arkanalyzer/out/src/save/serializeArkIR.ts -m ts ir
```

## Serialize sample projects and test the deserialization

To test the serialization/deserialization pipeline in jacodb, first prepare and serialize the projects using `prepare_repos.sh` (pulls repos with sources) and `prepare_projects.sh` (serializes all projects) scripts:

```shell
cd jacodb-ets/src/test/resources
bash prepare_repos.sh
bash prepare_projects.sh
```

(Use `-f` flag for `prepare_projects.sh` to force re-serialization, that is, override already existing folders.)

Then, to test the serialization, run jacodb tests devoted to the serialization:

```shell

The, run jacodb tests devoted to the deserialization:

```shell
gw :jacodb-ets:test --tests "org.jacodb.ets.test.EtsFromJsonTest"
```
