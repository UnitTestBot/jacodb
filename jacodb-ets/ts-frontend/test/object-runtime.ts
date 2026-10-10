import { EtsFileDto, MethodDto } from "../src/dto/model";
import { LexicalEnvTypeDto } from "../src/dto/types";
import { ClosureFieldRefDto } from "../src/dto/values";

/**
 * A deliberately small concrete consumer for object-lowering regressions.
 * Executes serialized EtsIR using JavaScript property operations; this is not
 * the USVM interpreter and rejects every operation outside the tested subset.
 */
export function executeObjectIr(file: EtsFileDto, name: string, args: unknown[] = []): unknown {
    const methods = file.classes.flatMap((clazz) => clazz.methods);
    const moduleValues = new Map<string, unknown>();
    const lookup = (signature: any): MethodDto => {
        const method = methods.find((candidate) => candidate.signature.name === signature.name
            && candidate.signature.declaringClass.name === signature.declaringClass.name);
        if (method === undefined) throw new Error(`unknown test method ${signature.name}`);
        return method;
    };
    const defineData = (target: object, key: PropertyKey, value: unknown): void => {
        Object.defineProperty(target, key, { value, writable: true, enumerable: true, configurable: true });
    };
    const invoke = (method: MethodDto, supplied: unknown[], receiver: unknown): unknown => {
        if (method.body === undefined) throw new Error(`bodyless test method ${method.signature.name}`);
        const locals = new Map<string, unknown>();
        const environment = (type: LexicalEnvTypeDto): object => {
            const slots = new Set(type.closures.map((capture) => capture.name));
            const checkSlot = (key: PropertyKey): string => {
                const name = String(key);
                if (!slots.has(name)) throw new Error(`undeclared test capture ${name}`);
                return name;
            };

            return new Proxy({}, {
                get: (_target, key) => locals.get(checkSlot(key)),
                set: (_target, key, value) => {
                    locals.set(checkSlot(key), value);
                    return true;
                },
            });
        };
        const captureEnvironment = (reference: ClosureFieldRefDto): Record<string, unknown> => {
            const type = reference.base.type;
            if (type._ !== "LexicalEnvType" || !type.closures.some((capture) => capture.name === reference.fieldName)) {
                throw new Error(`undeclared test capture ${reference.fieldName}`);
            }

            const captured = locals.get(reference.base.name);
            if (captured === null || typeof captured !== "object") {
                throw new Error(`missing test closure environment ${reference.base.name}`);
            }
            return captured as Record<string, unknown>;
        };
        const read = (value: any): any => {
            switch (value._) {
                case "Local": {
                    if (!locals.has(value.name) && value.type._ === "FunctionType") {
                        const lifted = lookup(value.type.signature);
                        const environmentType = lifted.signature.parameters[0]?.type;
                        const lexicalEnvironment = environmentType?._ === "LexicalEnvType"
                            ? environment(environmentType)
                            : undefined;

                        locals.set(value.name, function (this: unknown, ...callArgs: unknown[]) {
                            const supplied = lexicalEnvironment === undefined ? callArgs : [lexicalEnvironment, ...callArgs];
                            return invoke(lifted, supplied, this);
                        });
                    }
                    return locals.get(value.name);
                }
                case "Constant":
                    switch (value.type._) {
                        case "StringType": return value.value;
                        case "NumberType": return Number(value.value);
                        case "BooleanType": return value.value === "true";
                        case "NullType": return null;
                        case "UndefinedType": return undefined;
                        default: throw new Error(`unknown constant ${value.type._}`);
                    }
                case "ParameterRef": return supplied[value.index];
                case "ThisRef": return receiver;
                case "NewExpr": return {};
                case "StaticFieldRef": return moduleValues.get(value.field.name);
                case "ClosureFieldRef": return captureEnvironment(value)[value.fieldName];
                case "InstanceFieldRef": return read(value.instance)[value.field.name];
                case "PropertyRef": return read(value.instance)[read(value.key)];
                case "ArrayRef": return read(value.array)[read(value.index)];
                case "ToPropertyKeyExpr": return Reflect.ownKeys({ [read(value.arg)]: 0 })[0];
                case "StaticCallExpr": return invoke(lookup(value.method), value.args.map(read), undefined);
                case "InstanceCallExpr": {
                    const object = read(value.instance);
                    const member = object[value.method.name];
                    return typeof member === "function"
                        ? member.apply(object, value.args.map(read))
                        : invoke(lookup(value.method), value.args.map(read), object);
                }
                case "PtrCallExpr": return Reflect.apply(
                    read(value.ptr),
                    value.receiver === undefined ? undefined : read(value.receiver),
                    value.args.map(read),
                );
                case "CastExpr": return read(value.arg);
                case "UnopExpr":
                    if (value.op === "+") return +read(value.arg);
                    if (value.op === "++") return Number(read(value.arg)) + 1;
                    throw new Error(`unknown unary operator ${value.op}`);
                case "ConditionExpr":
                    switch (value.op) {
                        case "===": return read(value.left) === read(value.right);
                        case "!==": return read(value.left) !== read(value.right);
                        case "!=": return read(value.left) != read(value.right);
                        case "==": return read(value.left) == read(value.right);
                        default: throw new Error(`unknown relation ${value.op}`);
                    }
                case "BinopExpr":
                    switch (value.op) {
                        case "+": return read(value.left) + read(value.right);
                        case "*": return read(value.left) * read(value.right);
                        default: throw new Error(`unknown binary operator ${value.op}`);
                    }
                default: throw new Error(`unsupported object test value ${value._}`);
            }
        };
        const assign = (left: any, value: unknown): void => {
            switch (left._) {
                case "Local": locals.set(left.name, value); return;
                case "StaticFieldRef": moduleValues.set(left.field.name, value); return;
                case "ClosureFieldRef": captureEnvironment(left)[left.fieldName] = value; return;
                case "InstanceFieldRef": read(left.instance)[left.field.name] = value; return;
                case "PropertyRef": read(left.instance)[read(left.key)] = value; return;
                default: throw new Error(`unsupported object test assignment ${left._}`);
            }
        };
        const blocks = method.body.cfg.blocks;
        let block = blocks[0];
        for (let steps = 0; block !== undefined && steps < 1000; steps++) {
            let next = block.successors[0];
            for (const stmt of block.stmts) {
                switch (stmt._) {
                    case "AssignStmt": assign(stmt.left, read(stmt.right)); break;
                    case "DefineDataPropertyStmt": defineData(read(stmt.target), read(stmt.key), read(stmt.value)); break;
                    case "DefineAccessorStmt": {
                        const target = read(stmt.target);
                        const key = read(stmt.key);
                        const getter = read(stmt.getter);
                        const old = Object.getOwnPropertyDescriptor(target, key);
                        Object.defineProperty(target, key, {
                            get: getter,
                            set: old?.set,
                            enumerable: true,
                            configurable: true,
                        });
                        break;
                    }
                    case "CopyDataPropertiesStmt": {
                        const source = read(stmt.source);
                        if (source === null || source === undefined) {
                            if (stmt.throwOnNullishSource) throw new TypeError("nullish object rest");
                            break;
                        }
                        const object = Object(source);
                        const target = read(stmt.target);
                        const excluded = stmt.excludedKeys.map(read);
                        for (const key of Reflect.ownKeys(object)) {
                            if (!excluded.includes(key) && Object.getOwnPropertyDescriptor(object, key)?.enumerable) {
                                defineData(target, key, object[key]);
                            }
                        }
                        break;
                    }
                    case "IfStmt": next = block.successors[read(stmt.condition) ? 1 : 0]; break;
                    case "ReturnStmt": return read(stmt.arg);
                    case "ReturnVoidStmt": return undefined;
                    case "ThrowStmt": throw read(stmt.arg);
                    case "CallStmt": read(stmt.expr); break;
                    case "NopStmt": break;
                    default: throw new Error(`unsupported object test statement ${stmt._}`);
                }
            }
            block = blocks[next];
        }
        if (block !== undefined) throw new Error("object test exceeded step limit");
        return undefined;
    };

    const module = methods.find((method) => method.signature.name === "%dflt");
    if (module !== undefined) invoke(module, [], undefined);
    const method = methods.find((method) => method.signature.name === name);
    if (method === undefined) throw new Error(`missing object test method ${name}`);
    return invoke(method, args, undefined);
}
