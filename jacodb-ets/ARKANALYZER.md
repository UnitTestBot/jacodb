# ArkAnalyzer

## Installation

Clone and install the ArkAnalyzer via NPM:

```shell
cd ~/dev
git clone https://gitee.com/openharmony-sig/arkanalyzer
cd arkanalyzer
npm install
npm run build
```

The `npm run build` command will generate the `out` directory, which contains the compiled code.

### Usage of forked ArkAnalyzer

Most probably, you will have to use our fork of ArkAnalyzer (https://gitee.com/Lipenx/arkanalyzer) and checkout a specific branch that is consistent with the current state of jacodb.
For this, replace the repo url in the commands above and use `git switch <branch>` to checkout the desired branch.

> Latest supported AA branch is `neo/2025-02-13`.

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
