import { NAMED_FUNCTION_REFERENCE_PREFIX } from "../src/dto/constants";
import { EtsFileDto, MethodDto } from "../src/dto/model";
import { ValueDto } from "../src/dto/values";

/** Small concrete oracle for this regression suite. Unknown IR fails loudly.
 * Calls consume raw arguments and pack isRest on entry, matching TsInterpreter.
 * JavaScript supplies built-in/iterator operations; this is not a USVM execution test.
 */
export function executor(file: EtsFileDto, externals: Record<string, any> = {}) {
    const fields = new Map<string, any>();
    const methods = file.classes.flatMap((clazz) => clazz.methods);
    const templates = new Map<string, readonly unknown[]>();
    let initialized = false;

    const run = (method: MethodDto, args: any[], thisValue?: any): any => {
        if (method.body === undefined) throw new Error(`No body for ${method.signature.name}`);
        const parameters = method.signature.parameters.map((parameter, index) =>
            parameter.isRest ? args.slice(index) : args[index],
        );
        const locals = new Map<string, any>(Object.entries(externals));
        let caught: any;
        const value = (v: ValueDto): any => {
            switch (v._) {
                case "Local": {
                    if (v.name.startsWith(NAMED_FUNCTION_REFERENCE_PREFIX) && !locals.has(v.name)
                        && v.type._ === "FunctionType") {
                        const signature = v.type.signature;
                        const target = methods.find((candidate) => candidate.signature.name === signature.name
                            && JSON.stringify(candidate.signature.declaringClass) === JSON.stringify(signature.declaringClass));
                        if (target === undefined) throw new Error(`Unknown named function ${signature.name}`);
                        locals.set(v.name, function (this: any, ...callArgs: any[]): any {
                            return run(target, callArgs, this);
                        });
                    }
                    return locals.get(v.name);
                }
                case "TemplateObjectExpr": {
                    let template = templates.get(v.siteId);
                    if (template === undefined) {
                        const cooked = v.cooked.map((part) => part === null ? undefined : part);
                        Object.defineProperty(cooked, "raw", { value: Object.freeze([...v.raw]) });
                        template = Object.freeze(cooked);
                        templates.set(v.siteId, template);
                    }
                    return template;
                }
                case "Constant":
                    switch (v.type._) {
                        case "UndefinedType": return undefined;
                        case "NullType": return null;
                        case "NumberType": return Number(v.value);
                        case "BigIntType": return BigInt(v.value);
                        case "BooleanType": return v.value === "true";
                        default: return v.value;
                    }
                case "ParameterRef": return parameters[v.index];
                case "CaughtExceptionRef": return caught;
                case "ThisRef": return thisValue;
                case "StaticFieldRef": return fields.get(v.field.name);
                case "InstanceFieldRef": return value(v.instance)[v.field.name];
                case "ArrayRef": return value(v.array)[value(v.index)];
                case "NewArrayExpr": return new Array(value(v.size));
                case "NewExpr": {
                    if (v.classType._ !== "ClassType") throw new Error("Unsupported allocation type");
                    return { allocation: v.classType.signature.name };
                }
                case "TypeOfExpr": return typeof value(v.arg);
                case "CastExpr": return value(v.arg);
                case "ToNumericExpr": return -(-value(v.arg));
                case "UnopExpr": {
                    if (v.op === "!") return !value(v.arg);
                    if (v.op === "-") return -value(v.arg);
                    let numeric = value(v.arg);
                    if (v.op === "++") return ++numeric;
                    if (v.op === "--") return --numeric;
                    throw new Error(`Unsupported unary op: ${v.op}`);
                }
                case "BinopExpr":
                case "ConditionExpr": {
                    const left = value(v.left);
                    const right = value(v.right);
                    switch (v.op) {
                        case "+": return left + right;
                        case "-": return left - right;
                        case "*": return left * right;
                        case "==": return left == right;
                        case "===": return left === right;
                        case "!==": return left !== right;
                        case "<": return left < right;
                        case "<=": return left <= right;
                        case ">": return left > right;
                        case ">=": return left >= right;
                        default: throw new Error(`Unsupported binary op: ${v.op}`);
                    }
                }
                case "StaticCallExpr": {
                    const target = methods.find((candidate) => candidate.signature.name === v.method.name);
                    return target?.body !== undefined
                        ? run(target, v.args.map(value))
                        : externals[v.method.name](...v.args.map(value));
                }
                case "PtrCallExpr": return Reflect.apply(value(v.ptr), v.receiver === undefined ? undefined : value(v.receiver), v.args.map(value));
                case "InstanceCallExpr": {
                    const instance = value(v.instance);
                    const callArgs = v.args.map(value);
                    if (v.method.name === "constructor") {
                        const ctor = (globalThis as any)[instance.allocation];
                        if (typeof ctor !== "function") throw new Error(`Unsupported constructor ${instance.allocation}`);
                        return Reflect.construct(ctor, callArgs);
                    }
                    const key = v.method.name === "Symbol.iterator" ? Symbol.iterator : v.method.name;
                    return Reflect.apply(instance[key], instance, callArgs);
                }
                default: throw new Error(`Unsupported value: ${v._}`);
            }
        };
        const assign = (target: any, result: any): void => {
            switch (target._) {
                case "Local": locals.set(target.name, result); return;
                case "StaticFieldRef": fields.set(target.field.name, result); return;
                case "InstanceFieldRef": value(target.instance)[target.field.name] = result; return;
                case "ArrayRef": value(target.array)[value(target.index)] = result; return;
                default: throw new Error(`Unsupported assignment: ${target._}`);
            }
        };

        let blockId = 0;
        for (let steps = 0; steps < 100_000; steps++) {
            const block = method.body.cfg.blocks[blockId];
            let next = block.successors[0];
            for (let index = 0; index < block.stmts.length; index++) {
                const stmt = block.stmts[index];
                try {
                    switch (stmt._) {
                        case "AssignStmt": assign(stmt.left, value(stmt.right)); break;
                        case "CallStmt": value(stmt.expr); break;
                        case "NopStmt": break;
                        case "ReturnStmt": return value(stmt.arg);
                        case "ReturnVoidStmt": return undefined;
                        case "ThrowStmt": throw value(stmt.arg);
                        case "IfStmt": next = block.successors[value(stmt.condition) ? 1 : 0]; break;
                        default: throw new Error(`Unsupported statement: ${(stmt as any)._}`);
                    }
                } catch (error) {
                    const handler = block.exceptionalSuccessors?.find((edge) => edge.stmtIndex === index);
                    if (handler === undefined) throw error;
                    caught = error;
                    next = handler.target;
                    break;
                }
            }
            if (next === undefined) throw new Error("Missing CFG successor");
            blockId = next;
        }
        throw new Error("Concrete oracle step limit exceeded");
    };

    const initialize = (): void => {
        if (initialized) return;
        const method = methods.find((candidate) => candidate.signature.name === "%dflt");
        if (method !== undefined) run(method, []);
        initialized = true;
    };

    return {
        call(name: string, ...args: any[]): any {
            initialize();
            const method = methods.find((candidate) => candidate.signature.name === name);
            if (method === undefined) throw new Error(`No method ${name}`);
            return run(method, args);
        },
        initialize,
    };
}
