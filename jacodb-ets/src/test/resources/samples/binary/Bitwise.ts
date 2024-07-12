// comments for future symbolic support

const a = 5;   // 0101 in binary
const b = 3;   // 0011 in binary

const andResult = a & b;  // 0101 & 0011 = 0001 (1)
const orResult = a | b;   // 0101 | 0011 = 0111 (7)
const xorResult = a ^ b;  // 0101 ^ 0011 = 0110 (6)
const notResult = ~a;     // ~0101 = 1010 (which is -6 in 32-bit integer)
const leftShiftResult = a << 1;  // 0101 << 1 = 1010 (10)
const rightShiftResult = a >> 1;  // 0101 >> 1 = 0010 (2)
const unsignedRightShiftResult = a >>> 1;  // 0101 >>> 1 = 0010 (2)

const c = -5; // In binary: 11111111111111111111111111111011

// logical right shift on a negative number
const unsignedRightShiftNegativeResult = c >>> 1; // 01111111111111111111111111111101 (2147483645)
