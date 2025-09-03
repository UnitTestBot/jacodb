// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols

// Empty enum (edge case)
enum EmptyEnum {
    // No members
}

// Single member enum
enum SingleEnum {
    OnlyOne
}

// Enum with float values
enum FloatEnum {
    Pi = 3.14,
    E = 2.71,
    Phi = 1.618
}

// Enum with very long names
enum VeryLongNameEnum {
    ThisIsAVeryLongEnumMemberNameThatTestsTheParsingCapability,
    AnotherExtremelyLongEnumMemberNameForTestingPurposes,
    ShortName
}

// Enum with special characters in string values
enum SpecialCharEnum {
    NewLine = "line1\nline2",
    Tab = "col1\tcol2",
    Quote = "He said \"Hello\"",
    Backslash = "path\\to\\file",
    Unicode = "café 🚀 ñoño"
}

// Enum with duplicate values (different keys, same values)
enum DuplicateValueEnum {
    Primary = 1,
    Secondary = 1, // Same value as Primary
    Tertiary = 2
}

// Enum members that look like reserved words
enum ReservedWordLikeEnum {
    class = "class_value",
    function = "function_value",
    var = "var_value",
    let = "let_value",
    const = "const_value"
}

// Enum with number-like string values
enum NumberStringEnum {
    Zero = "0",
    One = "1",
    Two = "2",
    NotANumber = "NaN"
}

// Nested enum access patterns
namespace NestedAccess {
    export enum InnerEnum {
        Value1 = "inner1",
        Value2 = "inner2"
    }

    export function useInnerEnum(): InnerEnum {
        return InnerEnum.Value1;
    }
}

// Enum used in generic constraints
interface EnumConstraint<T extends StringEnum> {
    value: T;

    process(input: T): void;
}

// Enum with computed values using other enums
enum CrossReferenceEnum {
    Base = DuplicateValueEnum.Primary,
    Extended = Base + 10,
    Combined = FloatEnum.Pi + Base
}

// Enum used in type unions
type ColorOrNumber = StringEnum | NumericEnum;

// Enum used as object key type
type EnumAsKey = {
    [K in StringEnum]: boolean;
};

// Enum with function call values
enum FunctionCallEnum {
    Random = Math.random(),
    Length = "hello".length,
    Parsed = parseInt("42")
}

// Enum access through different patterns
class EnumAccessPatterns {
    // Bracket notation access
    getBracketAccess(): string {
        return StringEnum["Red"];
    }

    // Dynamic access
    getDynamicAccess(key: keyof typeof StringEnum): string {
        return StringEnum[key];
    }

    // Computed property access
    getComputedAccess(): string {
        const prop = "Red" as keyof typeof StringEnum;
        return StringEnum[prop];
    }

    // Enum used in array
    getEnumArray(): StringEnum[] {
        return [StringEnum.Red, StringEnum.Green, StringEnum.Blue];
    }

    // Enum used in object literal
    getEnumObject(): { color: StringEnum } {
        return { color: StringEnum.Red };
    }

    // Enum used in ternary operator
    getConditionalEnum(condition: boolean): StringEnum {
        return condition ? StringEnum.Red : StringEnum.Blue;
    }
}

// Enum with getter-like access
namespace EnumWithGetters {
    export enum Status {
        Active = "ACTIVE",
        Inactive = "INACTIVE"
    }

    export function isActive(status: Status): boolean {
        return status === Status.Active;
    }
}

// Enum value used in calculations
function calculateWithEnum(): number {
    return NumericEnum.Ten * 2 + NumericEnum.One;
}

// Enum used in template literals
function createMessage(status: StringEnum): string {
    return `The color is ${status} today`;
}

// Enum comparison with different patterns
function compareEnumValues(): boolean[] {
    return [
        // Direct comparison
        StringEnum.Red === StringEnum.Red,
        // Comparison with variable
        StringEnum.Red === StringEnum["Red"],
        // Type coercion comparison
        NumericEnum.One == 1,
        // Strict comparison
        NumericEnum.One === 1,
    ];
}

// Enum used in class inheritance/implementation patterns
abstract class EnumProcessor<T> {
    abstract process(value: T): string;
}

class StringEnumProcessor extends EnumProcessor<StringEnum> {
    process(value: StringEnum): string {
        return `Processing: ${value}`;
    }
}

// Inline enums from other files for testing
enum StringEnum {
    Red = "red",
    Green = "green",
    Blue = "blue"
}

enum NumericEnum {
    One = 1,
    Two = 2,
    Ten = 10
}
