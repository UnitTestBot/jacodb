import * as ts from "typescript";

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
