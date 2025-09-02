// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols

// Exported enum for external use
export enum PublicEnum {
    Public1 = "public1",
    Public2 = "public2",
    Public3 = "public3"
}

// Default exported enum
enum DefaultExportedEnum {
    Default1,
    Default2,
    Default3
}

export default DefaultExportedEnum;

// Const enum export
export const enum ConstExportEnum {
    Small = "small",
    Medium = "medium",
    Large = "large"
}

// Enum with re-export pattern
export enum ReExportEnum {
    Option1 = 1,
    Option2 = 2
}

// Re-export with alias
export { ReExportEnum as AliasedEnum };

// Enum used in exported function
export function processEnum(value: PublicEnum): string {
    return `Processing ${value}`;
}

// Enum used in exported class
export class EnumConsumer {
    private status: PublicEnum = PublicEnum.Public1;

    constructor(initialStatus: PublicEnum) {
        this.status = initialStatus;
    }

    getStatus(): PublicEnum {
        return this.status;
    }

    setStatus(status: PublicEnum): void {
        this.status = status;
    }
}

// Enum used in exported interface
export interface EnumInterface {
    type: PublicEnum;

    process(input: PublicEnum): PublicEnum;
}

// Exported enum with namespace merging
export enum MergedEnum {
    Value1 = "value1",
    Value2 = "value2",
}

export namespace MergedEnum {
    export function helper(value: MergedEnum): boolean {
        return value === MergedEnum.Value1;
    }

    export const metadata = {
        version: "1.0",
        description: "Merged enum with namespace",
    };
}

// Enum used in type exports
export type EnumUnion = PublicEnum | DefaultExportedEnum;
export type EnumRecord = Record<PublicEnum, string>;
export type EnumKeys = keyof typeof PublicEnum;

// Conditional enum export
const isDevelopment = process.env.NODE_ENV === 'development';

export enum ConditionalEnum {
    Always = "always",
    Sometimes = isDevelopment ? "debug" : "production"
}

// Enum with computed exports
export enum ComputedExportEnum {
    Base = 10,
    Computed = Base * 2,
    Dynamic = Math.floor(Math.random() * 100)
}

// Multiple enum exports in one statement
enum LocalEnum1 {
    A = "a", B = "b"
}

enum LocalEnum2 {
    X = 1, Y = 2
}

export { LocalEnum1, LocalEnum2 };

// Enum used in exported generic
export class GenericWithEnum<T extends PublicEnum> {
    constructor(private value: T) {
    }

    getValue(): T {
        return this.value;
    }
}

// Enum used in exported decorator (if supported)
export function enumValidator(validValues: PublicEnum[]) {
    return function (target: any, propertyName: string, descriptor: PropertyDescriptor) {
        // Decorator logic would go here
    };
}

// Enum with barrel export pattern
export enum Feature1Enum {
    Option1 = "feature1_option1",
    Option2 = "feature1_option2"
}

export enum Feature2Enum {
    Option1 = "feature2_option1",
    Option2 = "feature2_option2"
}

// Would typically be in an index.ts file
export * from './enum-modules'; // Self-reference for testing

// Enum with side effects on export
let sideEffectCounter = 0;

export enum SideEffectEnum {
    First = ++sideEffectCounter,
    Second = ++sideEffectCounter,
    Third = ++sideEffectCounter
}

// Enum used in async context
export async function getAsyncEnum(): Promise<PublicEnum> {
    // Simulate async operation
    await new Promise(resolve => setTimeout(resolve, 0));
    return PublicEnum.Public1;
}

// Enum used in generator function
export function* enumGenerator(): Generator<PublicEnum> {
    yield PublicEnum.Public1;
    yield PublicEnum.Public2;
    yield PublicEnum.Public3;
}

// Enum with complex module patterns
export namespace EnumModule {
    export enum InternalEnum {
        Internal1 = "internal1",
        Internal2 = "internal2"
    }

    export function useInternal(value: InternalEnum): string {
        return `Using ${value}`;
    }

    export namespace Nested {
        export enum DeepEnum {
            Deep1 = 1,
            Deep2 = 2
        }
    }
}

// Import-like patterns (simulated for testing)
import type { PublicEnum as ImportedEnum } from './enum-modules';

export function useImportedEnum(value: ImportedEnum): string {
    return `Imported: ${value}`;
}
