import * as ts from "typescript";
import { EtsFileDto, MethodDto } from "../src/dto/model";
import { StmtDto } from "../src/dto/stmts";
import { Diagnostics } from "../src/lowering/diagnostics";
import { buildEtsFile } from "../src/lowering/fileBuilder";
import { validateEtsFile } from "../src/validate";

export interface Compiled {
    program: ts.Program;
    checker: ts.TypeChecker;
    sourceFile: ts.SourceFile;
}

export const COMPILER_OPTIONS: ts.CompilerOptions = {
    target: ts.ScriptTarget.ES2020,
    module: ts.ModuleKind.CommonJS,
    strict: false,
    allowJs: true,
    checkJs: false,
    noEmit: true,
    skipLibCheck: true,
};

/** Compile an in-memory source file with the real TypeScript compiler + default libs. */
export function compile(source: string, fileName: string = "test.ts"): Compiled {
    const host = ts.createCompilerHost(COMPILER_OPTIONS);
    const originalGetSourceFile = host.getSourceFile.bind(host);
    const originalFileExists = host.fileExists.bind(host);
    const originalReadFile = host.readFile.bind(host);

    host.getSourceFile = (name, languageVersion, onError, shouldCreateNewSourceFile) => {
        if (name === fileName) {
            return ts.createSourceFile(fileName, source, languageVersion, /* setParentNodes */ true);
        }
        return originalGetSourceFile(name, languageVersion, onError, shouldCreateNewSourceFile);
    };
    host.fileExists = (name) => name === fileName || originalFileExists(name);
    host.readFile = (name) => (name === fileName ? source : originalReadFile(name));

    const program = ts.createProgram([fileName], COMPILER_OPTIONS, host);
    const sourceFile = program.getSourceFile(fileName);
    if (sourceFile === undefined) {
        throw new Error(`failed to load in-memory source file: ${fileName}`);
    }
    return { program, checker: program.getTypeChecker(), sourceFile };
}

/** Find the first node satisfying a predicate (depth-first). */
export function findNode<T extends ts.Node>(
    root: ts.Node,
    predicate: (node: ts.Node) => node is T,
): T | undefined {
    let result: T | undefined;
    const visit = (node: ts.Node): void => {
        if (result !== undefined) return;
        if (predicate(node)) {
            result = node;
            return;
        }
        ts.forEachChild(node, visit);
    };
    visit(root);
    return result;
}

export interface Lowered {
    file: EtsFileDto;
    diagnostics: Diagnostics;
}

/** Run the full lowering pipeline on an in-memory source; asserts invariants hold. */
export function lower(source: string, projectName: string = "proj", fileName: string = "test.ts"): Lowered {
    const { program, sourceFile } = compile(source, fileName);
    const diagnostics = new Diagnostics();
    const file = buildEtsFile(program, sourceFile, { projectName, fileName }, diagnostics);
    const violations = validateEtsFile(file);
    if (violations.length > 0) {
        throw new Error(`invariant violations:\n${violations.join("\n")}`);
    }
    return { file, diagnostics };
}

/** Lower one source of an in-memory project, including project-owned declaration files. */
export function lowerProject(sources: Readonly<Record<string, string>>, entryFileName: string): Lowered {
    const host = ts.createCompilerHost(COMPILER_OPTIONS);
    const originalGetSourceFile = host.getSourceFile.bind(host);
    const originalFileExists = host.fileExists.bind(host);
    const originalReadFile = host.readFile.bind(host);
    const hasSource = (fileName: string): boolean => Object.prototype.hasOwnProperty.call(sources, fileName);

    host.getSourceFile = (name, languageVersion, onError, shouldCreateNewSourceFile) => {
        const source = sources[name];
        return source !== undefined
            ? ts.createSourceFile(name, source, languageVersion, /* setParentNodes */ true)
            : originalGetSourceFile(name, languageVersion, onError, shouldCreateNewSourceFile);
    };
    host.fileExists = (name) => hasSource(name) || originalFileExists(name);
    host.readFile = (name) => sources[name] ?? originalReadFile(name);

    const program = ts.createProgram(Object.keys(sources), COMPILER_OPTIONS, host);
    const sourceFile = program.getSourceFile(entryFileName);
    if (sourceFile === undefined) {
        throw new Error(`failed to load in-memory source file: ${entryFileName}`);
    }
    const diagnostics = new Diagnostics();
    const file = buildEtsFile(program, sourceFile, {
        projectName: "proj",
        fileName: entryFileName,
        fileSignatureFor: (sf) => hasSource(sf.fileName)
            ? { projectName: "proj", fileName: sf.fileName }
            : { projectName: "%unk", fileName: "%unk" },
    }, diagnostics);
    const violations = validateEtsFile(file);
    if (violations.length > 0) {
        throw new Error(`invariant violations:\n${violations.join("\n")}`);
    }
    return { file, diagnostics };
}

/** The `%dflt` method of the `%dflt` class. */
export function defaultMethod(file: EtsFileDto): MethodDto {
    const clazz = file.classes.find((c) => c.signature.name === "%dflt");
    if (clazz === undefined) throw new Error("no %dflt class");
    const method = clazz.methods.find((m) => m.signature.name === "%dflt");
    if (method === undefined) throw new Error("no %dflt method");
    return method;
}

/** Method by name across all classes of the file. */
export function methodByName(file: EtsFileDto, name: string): MethodDto {
    for (const clazz of file.classes) {
        for (const method of clazz.methods) {
            if (method.signature.name === name) return method;
        }
    }
    throw new Error(`method '${name}' not found`);
}

/** All statements of a single-block body (asserts the body has exactly one block). */
export function singleBlockStmts(method: MethodDto): StmtDto[] {
    if (method.body === undefined) throw new Error("method has no body");
    const blocks = method.body.cfg.blocks;
    if (blocks.length !== 1) throw new Error(`expected 1 block, got ${blocks.length}`);
    return blocks[0].stmts;
}

/** Find the variable declaration with the given name. */
export function findVariable(root: ts.Node, name: string): ts.VariableDeclaration {
    const decl = findNode(
        root,
        (n): n is ts.VariableDeclaration =>
            ts.isVariableDeclaration(n) && ts.isIdentifier(n.name) && n.name.text === name,
    );
    if (decl === undefined) {
        throw new Error(`variable '${name}' not found`);
    }
    return decl;
}
