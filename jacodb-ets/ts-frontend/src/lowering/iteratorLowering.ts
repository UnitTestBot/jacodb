/** Shared iterator protocol for eager array spread and array-rest bindings. */
import { CONSTRUCTOR_NAME } from "../dto/constants";
import { UNKNOWN_CLASS_SIGNATURE, UNKNOWN_FILE_SIGNATURE } from "../dto/signatures";
import { BOOLEAN_TYPE, NUMBER_TYPE, STRING_TYPE, TypeDto, UNKNOWN_TYPE } from "../dto/types";
import { ImmediateDto, LocalDto } from "../dto/values";
import { MethodContext } from "./methodBuilder";

const number = (value: number): ImmediateDto => ({ _: "Constant", value: String(value), type: NUMBER_TYPE });
const bool = (value: boolean): ImmediateDto => ({ _: "Constant", value: String(value), type: BOOLEAN_TYPE });

export function throwRuntimeError(m: MethodContext, name: string, message: string): void {
    const classType: TypeDto = { _: "ClassType", signature: { name, declaringFile: UNKNOWN_FILE_SIGNATURE } };
    const error = m.newTemp(classType);
    m.cfg.emit({ _: "AssignStmt", left: error, right: { _: "NewExpr", classType } });
    m.cfg.emit({
        _: "AssignStmt", left: error,
        right: {
            _: "InstanceCallExpr", instance: error,
            method: {
                declaringClass: classType.signature, name: CONSTRUCTOR_NAME,
                parameters: [{ name: "message", type: STRING_TYPE }], returnType: classType,
            },
            args: [{ _: "Constant", value: message, type: STRING_TYPE }],
        },
    });
    m.cfg.throwValue(error);
}

/** Capture next once, as required by ECMAScript's Iterator Record. */
export class IteratorLowerer {
    private readonly iterator: LocalDto;
    private readonly next: LocalDto;
    private readonly exhausted: LocalDto;

    constructor(private readonly m: MethodContext, source: LocalDto) {
        this.iterator = m.newTemp(UNKNOWN_TYPE);
        m.cfg.emit({
            _: "AssignStmt", left: this.iterator,
            right: {
                _: "InstanceCallExpr", instance: source,
                method: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name: "Symbol.iterator", parameters: [], returnType: UNKNOWN_TYPE },
                args: [],
            },
        });
        this.requireObject(this.iterator);
        this.next = this.field(this.iterator, "next", UNKNOWN_TYPE);
        this.exhausted = m.newTemp(BOOLEAN_TYPE);
        m.cfg.emit({ _: "AssignStmt", left: this.exhausted, right: bool(false) });
    }

    /** Yield undefined after exhaustion without calling next again. */
    take(type: TypeDto, options: { readValue?: boolean } = {}): LocalDto {
        const cfg = this.m.cfg;
        const value = this.m.newTemp(type);
        cfg.emit({ _: "AssignStmt", left: value, right: { _: "Constant", value: "undefined", type: { _: "UndefinedType" } } });
        const step = cfg.newLabel();
        const read = cfg.newLabel();
        const done = cfg.newLabel();
        cfg.branch(this.isExhausted(), done, step);
        cfg.placeLabel(step);
        const result = this.advance();
        cfg.branch(this.isExhausted(), done, read);
        cfg.placeLabel(read);
        if (options.readValue !== false) {
            cfg.emit({ _: "AssignStmt", left: value, right: this.fieldRef(result, "value", type) });
        }
        cfg.goto(done);
        cfg.placeLabel(done);
        return value;
    }

    /** Drain into a fresh or partially built array in iteration order. */
    appendTo(target: LocalDto, offset: LocalDto, elementType: TypeDto): void {
        const cfg = this.m.cfg;
        const head = cfg.newLabel();
        const step = cfg.newLabel();
        const store = cfg.newLabel();
        const done = cfg.newLabel();
        cfg.placeLabel(head);
        cfg.branch(this.isExhausted(), done, step);
        cfg.placeLabel(step);
        const result = this.advance();
        cfg.branch(this.isExhausted(), done, store);
        cfg.placeLabel(store);
        const value = this.field(result, "value", elementType);
        cfg.emit({ _: "AssignStmt", left: { _: "ArrayRef", array: target, index: offset, type: elementType }, right: value });
        cfg.emit({ _: "AssignStmt", left: offset, right: { _: "BinopExpr", op: "+", left: offset, right: number(1), type: NUMBER_TYPE } });
        cfg.goto(head);
        cfg.placeLabel(done);
    }

    /** Abrupt binding initialization closes the iterator; the original throw wins. */
    closeOnException(action: () => void): void {
        const cfg = this.m.cfg;
        const close = cfg.newLabel();
        const lookup = cfg.newLabel();
        const invoke = cfg.newLabel();
        const rethrow = cfg.newLabel();
        const continuation = cfg.newLabel();
        cfg.withExceptionTarget(close, action);
        cfg.goto(continuation);
        cfg.placeLabel(close);
        const original = this.m.newTemp(UNKNOWN_TYPE);
        cfg.emit({ _: "AssignStmt", left: original, right: { _: "CaughtExceptionRef", type: UNKNOWN_TYPE } });
        cfg.branch(this.isExhausted(), rethrow, lookup);
        cfg.placeLabel(lookup);
        cfg.withExceptionTarget(rethrow, () => {
            const method = this.field(this.iterator, "return", UNKNOWN_TYPE);
            cfg.branch({ _: "ConditionExpr", op: "==", left: method, right: { _: "Constant", value: "null", type: { _: "NullType" } } }, rethrow, invoke);
            cfg.placeLabel(invoke);
            cfg.emit({
                _: "CallStmt",
                expr: {
                    _: "PtrCallExpr", ptr: method, receiver: this.iterator,
                    method: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name: "return", parameters: [], returnType: UNKNOWN_TYPE }, args: [],
                },
            });
            cfg.goto(rethrow);
        });
        cfg.placeLabel(rethrow);
        cfg.throwValue(original);
        cfg.placeLabel(continuation);
    }

    private advance(): LocalDto {
        const cfg = this.m.cfg;
        const result = this.m.newTemp(UNKNOWN_TYPE);
        cfg.emit({
            _: "AssignStmt", left: result,
            right: {
                _: "PtrCallExpr", ptr: this.next, receiver: this.iterator,
                method: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name: "next", parameters: [], returnType: UNKNOWN_TYPE },
                args: [],
            },
        });
        this.requireObject(result);
        const done = this.field(result, "done", UNKNOWN_TYPE);
        const notDone = this.m.newTemp(BOOLEAN_TYPE);
        cfg.emit({ _: "AssignStmt", left: notDone, right: { _: "UnopExpr", op: "!", arg: done } });
        cfg.emit({ _: "AssignStmt", left: this.exhausted, right: { _: "UnopExpr", op: "!", arg: notDone } });
        return result;
    }

    private requireObject(value: LocalDto): void {
        const cfg = this.m.cfg;
        const kind = this.m.newTemp(STRING_TYPE);
        cfg.emit({ _: "AssignStmt", left: kind, right: { _: "TypeOfExpr", arg: value } });
        const object = cfg.newLabel();
        const functionCheck = cfg.newLabel();
        const valid = cfg.newLabel();
        const invalid = cfg.newLabel();
        cfg.branch({ _: "ConditionExpr", op: "===", left: kind, right: { _: "Constant", value: "object", type: STRING_TYPE } }, object, functionCheck);
        cfg.placeLabel(object);
        cfg.branch({ _: "ConditionExpr", op: "===", left: value, right: { _: "Constant", value: "null", type: { _: "NullType" } } }, invalid, valid);
        cfg.placeLabel(functionCheck);
        cfg.branch({ _: "ConditionExpr", op: "===", left: kind, right: { _: "Constant", value: "function", type: STRING_TYPE } }, valid, invalid);
        cfg.placeLabel(invalid);
        throwRuntimeError(this.m, "TypeError", "Iterator result is not an object");
        cfg.placeLabel(valid);
    }

    private isExhausted() {
        return { _: "ConditionExpr" as const, op: "===" as const, left: this.exhausted, right: bool(true) };
    }

    private field(instance: LocalDto, name: string, type: TypeDto): LocalDto {
        const local = this.m.newTemp(type);
        this.m.cfg.emit({ _: "AssignStmt", left: local, right: this.fieldRef(instance, name, type) });
        return local;
    }

    private fieldRef(instance: LocalDto, name: string, type: TypeDto) {
        return { _: "InstanceFieldRef" as const, instance, field: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name, type } };
    }
}
