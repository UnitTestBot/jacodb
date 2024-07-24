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

You can also use `node <out>/serializeArkIR.js` directly (note the `.js` extension here!) instead of `npx`. Remember to run `npm run build` beforehand.

For example, to serialize ArkIR for all TS files in `resources/ts/` into the corresponding JSON files in `resources/ir/`, run:

```shell
cd .../resources
npx ts-node ~/dev/arkanalyzer/out/src/save/serializeArkIR.ts -m ts ir
```
