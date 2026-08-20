export enum Operation {
    Add,
    Sub,
    Mul,
    Div,
}

export interface HistoryEntry {
    op: Operation;
    left: number;
    right: number;
    result: number;
}

export class Calculator {
    private history: HistoryEntry[] = [];
    static instances: number = 0;

    constructor(private readonly precision: number = 2) {
        Calculator.instances++;
    }

    apply(op: Operation, left: number, right: number): number {
        let result: number;
        switch (op) {
            case Operation.Add:
                result = left + right;
                break;
            case Operation.Sub:
                result = left - right;
                break;
            case Operation.Mul:
                result = left * right;
                break;
            case Operation.Div:
                if (right === 0) {
                    throw new Error("division by zero");
                }
                result = left / right;
                break;
            default:
                throw new Error(`unknown operation: ${op}`);
        }
        this.history.push({ op, left, right, result });
        return result;
    }

    lastResult(): number | undefined {
        const entry = this.history[this.history.length - 1];
        return entry?.result;
    }

    undo(): boolean {
        if (this.history.length === 0) {
            return false;
        }
        this.history.pop();
        return true;
    }
}
