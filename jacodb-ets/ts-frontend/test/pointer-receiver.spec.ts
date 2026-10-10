import { expect, it } from "vitest";
import { UNKNOWN_CLASS_SIGNATURE } from "../src/dto/signatures";
import { UNKNOWN_TYPE } from "../src/dto/types";
import { LocalDto, PtrCallExprDto } from "../src/dto/values";
import { valueOperands } from "../src/validate";

it("preserves and traverses a captured pointer-call receiver exactly once", () => {
    const ptr: LocalDto = { _: "Local", name: "fn", type: UNKNOWN_TYPE };
    const receiver: LocalDto = { _: "Local", name: "object", type: UNKNOWN_TYPE };
    const argument: LocalDto = { _: "Local", name: "arg", type: UNKNOWN_TYPE };
    const call: PtrCallExprDto = {
        _: "PtrCallExpr", ptr, receiver,
        method: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name: "call", parameters: [], returnType: UNKNOWN_TYPE },
        args: [argument],
    };

    const roundTripped: PtrCallExprDto = JSON.parse(JSON.stringify(call));

    expect(roundTripped.receiver).toEqual(receiver);
    expect(valueOperands(roundTripped)).toEqual([ptr, receiver, argument]);
    expect(valueOperands({ ...call, receiver: undefined })).toEqual([ptr, argument]);
});
