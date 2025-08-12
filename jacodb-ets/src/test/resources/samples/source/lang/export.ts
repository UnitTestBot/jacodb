// Sample TypeScript file with various export statements for testing
// noinspection ES6UnusedImports,TypeScriptCheckImport,JSUnusedLocalSymbols,JSUnusedGlobalSymbols

// Named exports
export const publicConstant = 'hello';
export let publicVariable = 42;
export function publicFunction() {
    return 'public';
}

// Class export
export class PublicClass {
    constructor(public name: string) {}
}

// Interface export
export interface PublicInterface {
    id: number;
    name: string;
}

// Type export
export type PublicType = string | number;

// Enum export
export enum PublicEnum {
    FIRST = 'first',
    SECOND = 'second'
}

// Default exports
const defaultValue = 'default export value';
export default defaultValue;

// Re-exports
export { internalFunction } from './internal-module';
export { Component as ReactComponent } from 'react';
export * from './all-exports';
export * as Utils from './utils';

// Export with alias
const internalName = 'internal';
export { internalName as publicName };

// Namespace export
namespace MyNamespace {
    export function namespaceFunction() {
        return 'namespace';
    }
}
export { MyNamespace };

// Local declarations for internal use (not exported)
const privateConstant = 'private';
function privateFunction() {
    return 'private';
}
