// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols

// Basic numeric enum (auto-incrementing from 0)
enum BasicEnum {
    First,
    Second,
    Third
}

// Numeric enum with explicit values
enum NumericEnum {
    One = 1,
    Two = 2,
    Three = 3,
    Ten = 10,
    Eleven // auto-increment from 10, becomes 11
}

// String enum
enum StringEnum {
    Red = "red",
    Green = "green",
    Blue = "blue"
}

// Mixed enum (heterogeneous)
enum MixedEnum {
    No = 0,
    Yes = "YES",
    Maybe = 1
}

// Computed enum values
enum ComputedEnum {
    A = 1,
    B = A * 2,
    C = B + A,
    D = Math.floor(3.14)
}

// Enum with negative values
enum NegativeEnum {
    Below = -1,
    Zero = 0,
    Above = 1
}

// Const enum (compile-time constants)
const enum ConstEnum {
    Small = 1,
    Medium = 2,
    Large = 3
}

// Enum with string and template literal values
enum TemplateEnum {
    Start = "start",
    Process = `process_${Date.now()}`,
    End = "end"
}

// Enum as namespace (with additional properties/methods)
enum Direction {
    Up = "UP",
    Down = "DOWN",
    Left = "LEFT",
    Right = "RIGHT"
}

namespace Direction {
    export function opposite(dir: Direction): Direction {
        switch (dir) {
            case Direction.Up:
                return Direction.Down;
            case Direction.Down:
                return Direction.Up;
            case Direction.Left:
                return Direction.Right;
            case Direction.Right:
                return Direction.Left;
        }
    }
}

// Enum with large values
enum LargeEnum {
    Small = 1,
    Medium = 1000,
    Large = 1000000,
    Huge = Number.MAX_SAFE_INTEGER
}

// Enum usage scenarios
class EnumUsageExamples {
    // Property with enum type
    private status: BasicEnum = BasicEnum.First;

    // Method parameter with enum
    setColor(color: StringEnum): void {
        console.log(`Setting color to ${color}`);
    }

    // Method returning enum
    getDirection(): Direction.Up {
        return Direction.Up;
    }

    // Switch on enum
    handleDirection(dir: Direction): string {
        switch (dir) {
            case Direction.Up:
                return "Going up";
            case Direction.Down:
                return "Going down";
            case Direction.Left:
                return "Going left";
            case Direction.Right:
                return "Going right";
            default:
                return "Unknown direction";
        }
    }

    // Enum as object key
    getEnumValues(): Record<StringEnum, number> {
        return {
            [StringEnum.Red]: 1,
            [StringEnum.Green]: 2,
            [StringEnum.Blue]: 3,
        };
    }

    // Enum comparison
    compareEnums(): boolean {
        return BasicEnum.First === BasicEnum.First;
    }

    // Enum to string conversion
    enumToString(value: BasicEnum): string {
        return BasicEnum[value];
    }

    // String to enum conversion
    stringToEnum(str: string): BasicEnum | undefined {
        return (BasicEnum as any)[str];
    }
}

// Enum with bit flags pattern
enum FilePermission {
    None = 0,
    Read = 1 << 0,    // 1
    Write = 1 << 1,   // 2
    Execute = 1 << 2, // 4
    All = Read | Write | Execute // 7
}

// Enum iteration and introspection
function iterateEnum() {
    // Get all numeric enum keys
    const basicKeys = Object.keys(BasicEnum).filter(key => isNaN(Number(key)));

    // Get all numeric enum values
    const basicValues = Object.values(BasicEnum).filter(value => typeof value === 'number');

    // Get all string enum entries
    const stringEntries = Object.entries(StringEnum);

    return { basicKeys, basicValues, stringEntries };
}

// Enum as array index
const enumArray: string[] = [];
enumArray[BasicEnum.First] = "first item";
enumArray[BasicEnum.Second] = "second item";

// Enum with JSDoc comments
/**
 * HTTP status code categories
 */
enum HttpStatus {
    /** Informational responses */
    Continue = 100,
    /** Success responses */
    OK = 200,
    /** Client error responses */
    NotFound = 404,
    /** Server error responses */
    InternalServerError = 500
}

// Reverse mapping usage (for numeric enums)
function useReverseMapping() {
    const enumValue = BasicEnum.Second;
    const enumName = BasicEnum[enumValue]; // "Second"
    return { enumValue, enumName };
}

// Enum with complex expressions
enum ComplexEnum {
    Base = 10,
    Derived = Base * 2 + 5, // 25
    Complex = Math.max(Base, Derived) + 1 // 26
}

// Function using multiple enum types
function processMultipleEnums(
    basic: BasicEnum,
    str: StringEnum,
    dir: Direction,
): { basic: number | string, str: string, dir: string } {
    return {
        basic: basic,
        str: str,
        dir: dir,
    };
}

// Enum destructuring
const { Red, Green } = StringEnum;
const [first, second] = [BasicEnum.First, BasicEnum.Second];

// Enum with default export/import patterns (for module testing)
export { BasicEnum as DefaultBasicEnum };
export default StringEnum;
